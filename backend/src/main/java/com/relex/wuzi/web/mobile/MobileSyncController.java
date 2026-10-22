package com.relex.wuzi.web.mobile;

import com.relex.wuzi.annotation.LoginOnly;
import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.InventoryCheckItem;
import com.relex.wuzi.domain.TraceScanLog;
import com.relex.wuzi.mapper.InventoryCheckItemMapper;
import com.relex.wuzi.mapper.TraceScanLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 手机端离线批量同步：POST /api/m/sync/batch
 * <p>
 * 幂等：每条记录带 requestId，服务端去重后返回 INSERTED / DUPLICATE。
 * 主要落 trace_scan_log；盘点草稿按 checkId+materialId 回写 inventory_check_item.actualQty。
 */
@Slf4j
@RestController
@RequestMapping("/api/m/sync")
@RequiredArgsConstructor
public class MobileSyncController {

    private final TraceScanLogMapper scanLogMapper;
    private final InventoryCheckItemMapper checkItemMapper;

    /** 进程内 requestId 幂等表（单实例部署足够；多实例可在业务表补 unique request_id） */
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    @Data
    public static class ScanLogItem {
        private String requestId;
        private String code;
        private String qrCode;
        private String scanType;
        private String bizType;
        private Long bizId;
        private Long warehouseId;
        private String channel;
        private String result;
        private String failReason;
        private String scanTime;
        private String clientIp;
    }

    @Data
    public static class InventoryDraftItem {
        private String requestId;
        private Long checkId;
        private Long itemId;
        private Long materialId;
        private String batchNo;
        private Long locationId;
        private BigDecimal bookQty;
        private BigDecimal actualQty;
        private String remark;
    }

    @Data
    public static class BatchReq {
        private List<ScanLogItem> scanLogs;
        private List<InventoryDraftItem> inventoryDrafts;
    }

    @Data
    public static class ItemResult {
        private String requestId;
        private String kind;
        private String status;
        private String message;
        private Long id;
    }

    @LoginOnly("离线队列同步")
    @PostMapping("/batch")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> batch(@RequestBody BatchReq req,
                                        @RequestAttribute(value = "userId", required = false) Long userId,
                                        @RequestAttribute(value = "username", required = false) String username) {
        List<ItemResult> results = new ArrayList<>();
        int insertedScan = 0;
        int duplicatedScan = 0;
        int insertedDraft = 0;
        int duplicatedDraft = 0;

        if (req.getScanLogs() != null) {
            for (ScanLogItem item : req.getScanLogs()) {
                ItemResult r = new ItemResult();
                r.setKind("SCAN_LOG");
                r.setRequestId(item.getRequestId());
                String key = key("SCAN", item.getRequestId(), userId, item.getQrCode(), item.getCode(), item.getScanTime());
                if (!markSeen(key)) {
                    r.setStatus("DUPLICATE");
                    r.setMessage("requestId 已处理");
                    duplicatedScan++;
                    results.add(r);
                    continue;
                }
                try {
                    TraceScanLog log = new TraceScanLog();
                    log.setQrCode(truncate(first(item.getQrCode(), item.getCode()), 64));
                    log.setScanType(blank(item.getScanType()) ? "QUERY" : item.getScanType());
                    log.setBizType(item.getBizType());
                    log.setBizId(item.getBizId());
                    log.setOperatorId(userId);
                    log.setOperatorName(username);
                    log.setChannel(blank(item.getChannel()) ? "H5" : item.getChannel());
                    log.setWarehouseId(item.getWarehouseId());
                    log.setClientIp(item.getClientIp());
                    log.setResult(blank(item.getResult()) ? "SUCCESS" : item.getResult());
                    log.setFailReason(truncate(item.getFailReason(), 128));
                    log.setScanTime(parseTime(item.getScanTime()));
                    scanLogMapper.insert(log);
                    r.setStatus("INSERTED");
                    r.setId(log.getId());
                    insertedScan++;
                } catch (DuplicateKeyException e) {
                    r.setStatus("DUPLICATE");
                    r.setMessage("唯一键冲突");
                    duplicatedScan++;
                } catch (Exception e) {
                    unmarkSeen(key);
                    r.setStatus("FAILED");
                    r.setMessage(e.getMessage());
                    log.warn("离线扫码日志写入失败 requestId={}", item.getRequestId(), e);
                }
                results.add(r);
            }
        }

        if (req.getInventoryDrafts() != null) {
            for (InventoryDraftItem item : req.getInventoryDrafts()) {
                ItemResult r = new ItemResult();
                r.setKind("INVENTORY_DRAFT");
                r.setRequestId(item.getRequestId());
                String key = key("DRAFT", item.getRequestId(), userId,
                        item.getCheckId(), item.getItemId(), item.getMaterialId(), item.getActualQty());
                if (!markSeen(key)) {
                    r.setStatus("DUPLICATE");
                    r.setMessage("requestId 已处理");
                    duplicatedDraft++;
                    results.add(r);
                    continue;
                }
                try {
                    Long saved = applyDraft(item, userId);
                    r.setStatus("INSERTED");
                    r.setId(saved);
                    insertedDraft++;
                } catch (Exception e) {
                    unmarkSeen(key);
                    r.setStatus("FAILED");
                    r.setMessage(e.getMessage());
                    log.warn("离线盘点草稿写入失败 requestId={}", item.getRequestId(), e);
                }
                results.add(r);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("insertedScanLogs", insertedScan);
        data.put("duplicatedScanLogs", duplicatedScan);
        data.put("insertedDrafts", insertedDraft);
        data.put("duplicatedDrafts", duplicatedDraft);
        data.put("results", results);
        return R.ok(data);
    }

    private Long applyDraft(InventoryDraftItem item, Long userId) {
        if (item.getCheckId() == null) {
            throw new IllegalStateException("盘点草稿缺少 checkId");
        }
        if (item.getActualQty() == null) {
            throw new IllegalStateException("盘点草稿缺少 actualQty");
        }
        InventoryCheckItem row = null;
        if (item.getItemId() != null) {
            row = checkItemMapper.selectById(item.getItemId());
        }
        if (row == null && item.getMaterialId() != null) {
            row = checkItemMapper.selectOne(new LambdaQueryWrapper<InventoryCheckItem>()
                    .eq(InventoryCheckItem::getCheckId, item.getCheckId())
                    .eq(InventoryCheckItem::getMaterialId, item.getMaterialId())
                    .last("LIMIT 1"));
        }
        if (row == null) {
            row = new InventoryCheckItem();
            row.setCheckId(item.getCheckId());
            row.setMaterialId(item.getMaterialId());
            row.setBatchNo(item.getBatchNo());
            row.setLocationId(item.getLocationId());
            row.setBookQty(item.getBookQty() != null ? item.getBookQty() : BigDecimal.ZERO);
            row.setActualQty(item.getActualQty());
            BigDecimal book = row.getBookQty() != null ? row.getBookQty() : BigDecimal.ZERO;
            row.setDiffQty(item.getActualQty().subtract(book));
            row.setRemark(item.getRemark());
            checkItemMapper.insert(row);
            return row.getId();
        }
        row.setActualQty(item.getActualQty());
        if (item.getBookQty() != null) {
            row.setBookQty(item.getBookQty());
        }
        if (item.getBatchNo() != null) {
            row.setBatchNo(item.getBatchNo());
        }
        if (item.getLocationId() != null) {
            row.setLocationId(item.getLocationId());
        }
        if (item.getRemark() != null) {
            row.setRemark(item.getRemark());
        }
        BigDecimal book = row.getBookQty() != null ? row.getBookQty() : BigDecimal.ZERO;
        row.setDiffQty(item.getActualQty().subtract(book));
        checkItemMapper.updateById(row);
        return row.getId();
    }

    private static boolean markSeen(String key) {
        return SEEN.add(key);
    }

    private static void unmarkSeen(String key) {
        SEEN.remove(key);
    }

    private static String key(String kind, String requestId, Object... parts) {
        if (requestId != null && !requestId.isBlank()) {
            return kind + ":" + requestId;
        }
        StringBuilder sb = new StringBuilder(kind).append(":auto:");
        for (Object p : parts) {
            sb.append(p).append('|');
        }
        return sb.toString();
    }

    private static LocalDateTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T'));
        } catch (Exception e) {
            return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        }
    }

    private static String first(String a, String b) {
        return blank(a) ? b : a;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String truncate(String v, int max) {
        if (v == null) {
            return null;
        }
        return v.length() <= max ? v : v.substring(0, max);
    }
}
