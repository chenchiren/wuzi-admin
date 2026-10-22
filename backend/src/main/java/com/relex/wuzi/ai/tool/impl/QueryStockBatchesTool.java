package com.relex.wuzi.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.ai.service.AiLookupService;
import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.StockBatch;
import com.relex.wuzi.mapper.StockBatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批次库存查询（含生产日期/有效期）。全员可用。
 */
@Component
@RequiredArgsConstructor
public class QueryStockBatchesTool implements AiTool {

    private static final int MAX_ROWS = 20;

    private final AiLookupService lookup;
    private final StockBatchMapper batchMapper;

    @Override
    public String name() {
        return "query_stock_batches";
    }

    @Override
    public String label() {
        return "查询批次";
    }

    @Override
    public String description() {
        return "按物资名称查询批次库存（批号、生产日期、有效期、现存量，按有效期升序）。用户问「批次/有效期/什么时候到期」时使用。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("materialName", Map.of("type", "string", "description", "物资名称，支持部分匹配"));
        properties.put("warehouseName", Map.of("type", "string", "description", "仓库名称，可选"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("materialName"));
        return schema;
    }

    @Override
    public boolean allowedFor(ToolContext ctx) {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String materialName = QueryStockTool.str(args, "materialName");
        String warehouseName = QueryStockTool.str(args, "warehouseName");

        List<Material> mats = lookup.findMaterials(materialName);
        if (mats.isEmpty()) {
            return ToolResult.of("未找到名称包含「" + materialName + "」的物资", List.of(), "0行");
        }
        if (mats.size() > 3) {
            List<Map<String, Object>> cand = new ArrayList<>();
            for (Material m : mats) {
                cand.add(Map.of("materialCode", m.getMaterialCode(), "materialName", m.getMaterialName(), "spec", nvlStr(m.getSpec())));
            }
            return ToolResult.of("匹配到多件物资，请用户澄清", cand, "候选" + mats.size() + "件");
        }

        LambdaQueryWrapper<StockBatch> qw = new LambdaQueryWrapper<StockBatch>()
                .in(StockBatch::getMaterialId, mats.stream().map(Material::getId).toList())
                .gt(StockBatch::getQty, 0)
                .orderByAsc(StockBatch::getExpireDate)
                .last("LIMIT " + MAX_ROWS);
        if (warehouseName != null && !warehouseName.isBlank()) {
            var whs = lookup.findWarehouses(warehouseName);
            if (whs.size() == 1) {
                qw.eq(StockBatch::getWarehouseId, whs.get(0).getId());
            }
        }

        List<StockBatch> batches = batchMapper.selectList(qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StockBatch b : batches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("batchNo", b.getBatchNo());
            row.put("materialName", mats.get(0).getMaterialName());
            row.put("warehouseId", b.getWarehouseId());
            row.put("qty", b.getQty());
            row.put("productionDate", b.getProductionDate());
            row.put("expireDate", b.getExpireDate());
            rows.add(row);
        }
        String digest = "共" + rows.size() + "个批次"
                + (rows.isEmpty() ? "" : "，最近到期: " + rows.get(0).get("expireDate"));
        return ToolResult.of("共 " + rows.size() + " 个有效批次", rows, digest);
    }

    private static String nvlStr(String s) {
        return s == null ? "" : s;
    }
}
