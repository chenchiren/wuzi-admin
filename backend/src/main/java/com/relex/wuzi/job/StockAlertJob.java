package com.relex.wuzi.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.domain.AlertRecord;
import com.relex.wuzi.domain.StockBatch;
import com.relex.wuzi.mapper.AlertRecordMapper;
import com.relex.wuzi.mapper.StockBatchMapper;
import com.relex.wuzi.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存预警定时任务：每天扫描临期批次、低库存、超库存，写入 alert_record。
 * 临期天数可配置：wuzi.alert.expiry-days（默认 30）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockAlertJob {

    public static final String TYPE_EXPIRY = "EXPIRY";
    public static final String TYPE_LOW = "LOW_STOCK";
    public static final String TYPE_OVER = "OVER_STOCK";

    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_CRITICAL = "CRITICAL";

    private final StockBatchMapper stockBatchMapper;
    private final StockMapper stockMapper;
    private final AlertRecordMapper alertRecordMapper;

    @Value("${wuzi.alert.expiry-days:30}")
    private int expiryDays;

    /** 每天 08:00 扫描 */
    @Scheduled(cron = "0 0 8 * * ?")
    public void scanDaily() {
        int expiry = scanExpiry();
        int low = scanLowStock();
        int over = scanOverStock();
        log.info("StockAlertJob done: expiry={}, low={}, over={}", expiry, low, over);
    }

    /** 临期批次：expire_date 在 [today, today+expiryDays] 且仍有库存 */
    public int scanExpiry() {
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(Math.max(1, expiryDays));
        List<StockBatch> batches = stockBatchMapper.selectList(new LambdaQueryWrapper<StockBatch>()
                .isNotNull(StockBatch::getExpireDate)
                .ge(StockBatch::getExpireDate, today)
                .le(StockBatch::getExpireDate, deadline)
                .gt(StockBatch::getQty, 0));
        int n = 0;
        for (StockBatch b : batches) {
            long days = today.until(b.getExpireDate()).getDays();
            if (existsToday(TYPE_EXPIRY, b.getMaterialId(), b.getWarehouseId(), b.getBatchNo())) {
                continue;
            }
            AlertRecord r = new AlertRecord();
            r.setAlertType(TYPE_EXPIRY);
            r.setAlertLevel(days <= 7 ? LEVEL_CRITICAL : LEVEL_WARN);
            r.setTitle("物资临期预警");
            r.setContent("批次" + b.getBatchNo() + " 将于" + b.getExpireDate() + "到期（剩余" + days + "天），数量" + b.getQty());
            r.setMaterialId(b.getMaterialId());
            r.setWarehouseId(b.getWarehouseId());
            r.setBatchNo(b.getBatchNo());
            r.setQty(b.getQty());
            r.setThresholdVal(BigDecimal.valueOf(expiryDays));
            r.setExpireDate(b.getExpireDate());
            r.setBizNo(b.getBatchNo());
            r.setStatus("OPEN");
            r.setIsDeleted(0);
            alertRecordMapper.insert(r);
            n++;
        }
        return n;
    }

    /** 低库存 */
    public int scanLowStock() {
        List<Map<String, Object>> list = stockMapper.selectLowStock();
        int n = 0;
        for (Map<String, Object> row : list) {
            Long materialId = asLong(row.get("materialId"));
            Long warehouseId = asLong(row.get("warehouseId"));
            String batchNo = asStr(row.get("batchNo"));
            if (existsToday(TYPE_LOW, materialId, warehouseId, batchNo)) {
                continue;
            }
            AlertRecord r = baseFromRow(row, TYPE_LOW, LEVEL_WARN, "库存不足预警",
                    "当前库存低于安全库存");
            r.setThresholdVal(asDecimal(row.get("safetyStock")));
            alertRecordMapper.insert(r);
            n++;
        }
        return n;
    }

    /** 超库存 */
    public int scanOverStock() {
        List<Map<String, Object>> list = stockMapper.selectOverStock();
        int n = 0;
        for (Map<String, Object> row : list) {
            Long materialId = asLong(row.get("materialId"));
            Long warehouseId = asLong(row.get("warehouseId"));
            String batchNo = asStr(row.get("batchNo"));
            if (existsToday(TYPE_OVER, materialId, warehouseId, batchNo)) {
                continue;
            }
            AlertRecord r = baseFromRow(row, TYPE_OVER, LEVEL_WARN, "超库存预警",
                    "当前库存超过最高库存");
            r.setThresholdVal(asDecimal(row.get("maxStock")));
            alertRecordMapper.insert(r);
            n++;
        }
        return n;
    }

    private AlertRecord baseFromRow(Map<String, Object> row, String type, String level,
                                    String title, String contentPrefix) {
        String code = asStr(row.get("materialCode"));
        String name = asStr(row.get("materialName"));
        AlertRecord r = new AlertRecord();
        r.setAlertType(type);
        r.setAlertLevel(level);
        r.setTitle(title);
        r.setContent(contentPrefix + ": " + code + " " + name
                + "，数量" + row.get("qty"));
        r.setMaterialId(asLong(row.get("materialId")));
        r.setWarehouseId(asLong(row.get("warehouseId")));
        r.setBatchNo(asStr(row.get("batchNo")));
        r.setQty(asDecimal(row.get("qty")));
        r.setStatus("OPEN");
        r.setIsDeleted(0);
        return r;
    }

    /** 同日同类型同对象去重 */
    private boolean existsToday(String type, Long materialId, Long warehouseId, String batchNo) {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        return alertRecordMapper.selectCount(new LambdaQueryWrapper<AlertRecord>()
                .eq(AlertRecord::getAlertType, type)
                .eq(materialId != null, AlertRecord::getMaterialId, materialId)
                .eq(warehouseId != null, AlertRecord::getWarehouseId, warehouseId)
                .eq(batchNo != null, AlertRecord::getBatchNo, batchNo)
                .ge(AlertRecord::getCreatedAt, dayStart)) > 0;
    }

    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(v.toString());
    }

    private static String asStr(Object v) {
        return v == null ? null : v.toString();
    }

    private static BigDecimal asDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal d) {
            return d;
        }
        return new BigDecimal(v.toString());
    }
}
