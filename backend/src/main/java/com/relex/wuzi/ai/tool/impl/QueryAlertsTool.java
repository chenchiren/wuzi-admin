package com.relex.wuzi.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.domain.StockBatch;
import com.relex.wuzi.mapper.StockBatchMapper;
import com.relex.wuzi.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存预警：低库存 / 超库存 / 临期批次。全员可用。
 */
@Component
@RequiredArgsConstructor
public class QueryAlertsTool implements AiTool {

    private static final int MAX_ROWS = 20;

    private final StockMapper stockMapper;
    private final StockBatchMapper stockBatchMapper;

    @Value("${wuzi.alert.expiry-days:30}")
    private int expiryDays;

    @Override
    public String name() {
        return "query_alerts";
    }

    @Override
    public String label() {
        return "查询预警";
    }

    @Override
    public String description() {
        return "查询库存预警：type=low 低库存、type=over 超库存、type=expiry 临期批次。用户问「预警/缺货/临期」时使用。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("type", Map.of("type", "string", "enum", List.of("low", "over", "expiry"),
                "description", "预警类型"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("type"));
        return schema;
    }

    @Override
    public boolean allowedFor(ToolContext ctx) {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String type = QueryStockTool.str(args, "type");
        String t = type == null ? "" : type.toLowerCase();
        return switch (t) {
            case "low" -> low();
            case "over" -> over();
            case "expiry" -> expiry();
            default -> ToolResult.of("未知预警类型: " + type + "（支持 low/over/expiry）", List.of(), "参数错误");
        };
    }

    private ToolResult low() {
        List<Map<String, Object>> rows = cap(stockMapper.selectLowStock());
        return ToolResult.of("低库存 " + rows.size() + " 项", rows, "低库存" + rows.size() + "项");
    }

    private ToolResult over() {
        List<Map<String, Object>> rows = cap(stockMapper.selectOverStock());
        return ToolResult.of("超库存 " + rows.size() + " 项", rows, "超库存" + rows.size() + "项");
    }

    private ToolResult expiry() {
        LocalDate deadline = LocalDate.now().plusDays(expiryDays);
        List<StockBatch> batches = stockBatchMapper.selectList(new LambdaQueryWrapper<StockBatch>()
                .isNotNull(StockBatch::getExpireDate)
                .le(StockBatch::getExpireDate, deadline)
                .gt(StockBatch::getQty, 0)
                .orderByAsc(StockBatch::getExpireDate)
                .last("LIMIT " + MAX_ROWS));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StockBatch b : batches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("batchNo", b.getBatchNo());
            row.put("materialId", b.getMaterialId());
            row.put("warehouseId", b.getWarehouseId());
            row.put("qty", b.getQty());
            row.put("expireDate", b.getExpireDate());
            rows.add(row);
        }
        return ToolResult.of("距今 " + expiryDays + " 天内临期批次 " + rows.size() + " 个", rows,
                "临期" + rows.size() + "批" + (rows.isEmpty() ? "" : "，最近" + rows.get(0).get("expireDate")));
    }

    private static List<Map<String, Object>> cap(List<Map<String, Object>> in) {
        return new ArrayList<>(in.subList(0, Math.min(MAX_ROWS, in.size())));
    }
}
