package com.relex.wuzi.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.ai.service.AiLookupService;
import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.Stock;
import com.relex.wuzi.domain.Warehouse;
import com.relex.wuzi.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存余额查询（按物资名/仓库名）。全员可用。
 */
@Component
@RequiredArgsConstructor
public class QueryStockTool implements AiTool {

    private static final int MAX_ROWS = 20;

    private final AiLookupService lookup;
    private final StockMapper stockMapper;

    @Override
    public String name() {
        return "query_stock";
    }

    @Override
    public String label() {
        return "查询库存";
    }

    @Override
    public String description() {
        return "按物资名称（可带仓库名）查询当前库存余额，返回各仓/库位的现存量、冻结量、可用量。用户问「还有多少/库存多少」时使用。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> materialName = Map.of("type", "string", "description", "物资名称，支持部分匹配");
        Map<String, Object> warehouseName = Map.of("type", "string", "description", "仓库名称，可选");
        Map<String, Object> page = Map.of("type", "integer", "description", "页码，默认1");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("materialName", materialName);
        properties.put("warehouseName", warehouseName);
        properties.put("page", page);
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
        String materialName = str(args, "materialName");
        String warehouseName = str(args, "warehouseName");
        int page = intArg(args, "page", 1);

        List<Material> mats = lookup.findMaterials(materialName);
        if (mats.isEmpty()) {
            return ToolResult.of("未找到名称包含「" + materialName + "」的物资", List.of(), "0行");
        }
        if (mats.size() > 3) {
            return ToolResult.of("匹配到多件物资，请用户澄清", candidates(mats), "候选" + mats.size() + "件");
        }

        Long warehouseId = null;
        if (warehouseName != null && !warehouseName.isBlank()) {
            List<Warehouse> whs = lookup.findWarehouses(warehouseName);
            if (whs.isEmpty()) {
                return ToolResult.of("未找到名称包含「" + warehouseName + "」的仓库", List.of(), "0行");
            }
            if (whs.size() > 1) {
                return ToolResult.of("匹配到多个仓库，请用户澄清", warehouseCandidates(whs), "候选" + whs.size() + "仓");
            }
            warehouseId = whs.get(0).getId();
        }

        LambdaQueryWrapper<Stock> qw = new LambdaQueryWrapper<Stock>()
                .in(Stock::getMaterialId, mats.stream().map(Material::getId).toList())
                .eq(warehouseId != null, Stock::getWarehouseId, warehouseId)
                .orderByDesc(Stock::getQty);
        Page<Stock> p = stockMapper.selectPage(new Page<>(page, MAX_ROWS), qw);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Stock s : p.getRecords()) {
            BigDecimal qty = nvl(s.getQty());
            BigDecimal frozen = nvl(s.getFrozenQty());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialName", mats.get(0).getMaterialName());
            row.put("spec", mats.get(0).getSpec());
            row.put("unit", mats.get(0).getUnit());
            row.put("warehouseId", s.getWarehouseId());
            row.put("batchNo", s.getBatchNo());
            row.put("qty", qty);
            row.put("frozenQty", frozen);
            row.put("availableQty", qty.subtract(frozen).max(BigDecimal.ZERO));
            rows.add(row);
        }
        String digest = "共" + p.getTotal() + "行库存"
                + (rows.isEmpty() ? "" : "，首行: " + rows.get(0).get("materialName") + " 可用" + rows.get(0).get("availableQty"));
        return ToolResult.of("共 " + p.getTotal() + " 条库存记录", rows, digest);
    }

    private List<Map<String, Object>> candidates(List<Material> mats) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Material m : mats) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialCode", m.getMaterialCode());
            row.put("materialName", m.getMaterialName());
            row.put("spec", m.getSpec());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> warehouseCandidates(List<Warehouse> whs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Warehouse w : whs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("warehouseId", w.getId());
            row.put("warehouseName", w.getWarehouseName());
            rows.add(row);
        }
        return rows;
    }

    static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    static int intArg(Map<String, Object> args, String key, int def) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
