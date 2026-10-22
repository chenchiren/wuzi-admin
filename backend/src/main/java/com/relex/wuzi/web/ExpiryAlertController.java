package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.AlertRecord;
import com.relex.wuzi.domain.StockBatch;
import com.relex.wuzi.mapper.AlertRecordMapper;
import com.relex.wuzi.mapper.StockBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 临期预警 + 预警记录查询。
 * 临期：stock_batch.expire_date 距今 ≤ 可配置天数（默认 30）。
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class ExpiryAlertController {

    private final StockBatchMapper stockBatchMapper;
    private final AlertRecordMapper alertRecordMapper;

    @Value("${wuzi.alert.expiry-days:30}")
    private int expiryDays;

    /** 临期批次列表 */
    @GetMapping("/expiry")
    public R<List<Map<String, Object>>> expiry(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long warehouseId) {
        int d = days != null && days > 0 ? days : expiryDays;
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(d);
        List<StockBatch> batches = stockBatchMapper.selectList(new LambdaQueryWrapper<StockBatch>()
                .isNotNull(StockBatch::getExpireDate)
                .le(StockBatch::getExpireDate, deadline)
                .gt(StockBatch::getQty, 0)
                .eq(warehouseId != null, StockBatch::getWarehouseId, warehouseId)
                .orderByAsc(StockBatch::getExpireDate));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StockBatch b : batches) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", b.getId());
            row.put("batchNo", b.getBatchNo());
            row.put("materialId", b.getMaterialId());
            row.put("warehouseId", b.getWarehouseId());
            row.put("qty", b.getQty());
            row.put("productionDate", b.getProductionDate());
            row.put("expireDate", b.getExpireDate());
            long remainDays = today.until(b.getExpireDate()).getDays();
            row.put("remainDays", remainDays);
            row.put("expired", remainDays < 0);
            row.put("nearExpiry", remainDays >= 0 && remainDays <= d);
            rows.add(row);
        }
        return R.ok(rows);
    }

    /** 预警记录列表（临期/低库存/超库存） */
    @GetMapping("/record")
    public R<PageResult<AlertRecord>> records(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String alertType,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long materialId) {
        LambdaQueryWrapper<AlertRecord> qw = new LambdaQueryWrapper<AlertRecord>()
                .eq(alertType != null && !alertType.isBlank(), AlertRecord::getAlertType, alertType)
                .eq(status != null && !status.isBlank(), AlertRecord::getStatus, status)
                .eq(materialId != null, AlertRecord::getMaterialId, materialId)
                .orderByDesc(AlertRecord::getId);
        Page<AlertRecord> p = alertRecordMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 标记预警已读/已处理 */
    @RequirePerm("alert:update")
    @PostMapping("/record/{id}/read")
    public R<AlertRecord> markRead(@PathVariable Long id) {
        AlertRecord r = alertRecordMapper.selectById(id);
        if (r != null && "OPEN".equals(r.getStatus())) {
            r.setStatus("READ");
            alertRecordMapper.updateById(r);
        }
        return R.ok(r);
    }

    @RequirePerm("alert:update")
    @PostMapping("/record/{id}/handle")
    public R<AlertRecord> markHandled(@PathVariable Long id) {
        AlertRecord r = alertRecordMapper.selectById(id);
        if (r != null) {
            r.setStatus("HANDLED");
            alertRecordMapper.updateById(r);
        }
        return R.ok(r);
    }
}
