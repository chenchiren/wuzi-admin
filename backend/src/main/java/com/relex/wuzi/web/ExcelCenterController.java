package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.R;
import com.relex.wuzi.service.ExcelImportService;
import com.relex.wuzi.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 导入中心增强：错误明细查询 + 批次真回滚。
 * GET  /api/import/errors/{batchId}
 * POST /api/import/rollback/{batchId}
 */
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ExcelCenterController {

    private final ExcelImportService excelImportService;
    private final OperationLogService operationLogService;

    /**
     * 按 batchId 返回导入/预览错误明细。
     */
    @GetMapping("/errors/{batchId}")
    public R<List<ExcelImportService.ErrorRow>> errors(@PathVariable String batchId) {
        return R.ok(excelImportService.getBatchErrors(batchId));
    }

    /**
     * 回滚导入批次：按 import_batch_biz 逆序删除/作废已创建的物资或期初库存
     * 与对应 stock/stock_record（期初）。
     */
    @RequirePerm("sys:import:create")
    @PostMapping("/rollback/{batchId}")
    public R<Map<String, Object>> rollback(@PathVariable String batchId,
                                           @RequestAttribute(value = "userId", required = false) Long userId,
                                           @RequestAttribute(value = "username", required = false) String username,
                                           HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean live = excelImportService.discardBatch(batchId);
        boolean completed = excelImportService.markRolledBack(batchId);
        if (!live && !completed) {
            data.put("batchId", batchId);
            data.put("status", "NOT_FOUND");
            data.put("message", "批次不存在、已过期或已回滚");
            data.put("rolledBackAt", LocalDateTime.now());
            return R.ok(data);
        }
        data.put("batchId", batchId);
        data.put("status", "FAILED");
        data.put("message", live ? "预览批次已作废" : "导入批次已回滚（业务数据已删除/作废）");
        data.put("rolledBack", true);
        data.put("rolledBackAt", LocalDateTime.now());
        operationLogService.record(request, userId, username, ExcelImportService.LOG_MODULE,
                "IMPORT_ROLLBACK", "batchId=" + batchId, "FAILED");
        return R.ok(data);
    }

    /**
     * 批次错误数摘要，便于历史列表展示。
     */
    @GetMapping("/errors/{batchId}/summary")
    public R<Map<String, Object>> errorSummary(@PathVariable String batchId) {
        List<ExcelImportService.ErrorRow> rows = excelImportService.getBatchErrors(batchId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batchId", batchId);
        data.put("errorCount", rows.size());
        data.put("errors", rows);
        data.put("rolledBack", excelImportService.isRolledBack(batchId));
        return R.ok(data);
    }
}
