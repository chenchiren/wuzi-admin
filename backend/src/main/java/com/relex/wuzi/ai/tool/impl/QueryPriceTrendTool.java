package com.relex.wuzi.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.ai.service.AiLookupService;
import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.PurchaseOrder;
import com.relex.wuzi.domain.PurchaseOrderItem;
import com.relex.wuzi.domain.SupplierProductPrice;
import com.relex.wuzi.mapper.PurchaseOrderItemMapper;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.SupplierProductPriceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物资价格趋势：供应商目录价 + 采购订单实际价两个口径的散点。
 * 可见角色：PURCHASER / FINANCE / DEPT_MANAGER（ADMIN 自动放行）。
 */
@Component
@RequiredArgsConstructor
public class QueryPriceTrendTool implements AiTool {

    private static final int MAX_POINTS = 24;

    private final AiLookupService lookup;
    private final SupplierProductPriceMapper priceMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PurchaseOrderMapper orderMapper;

    @Override
    public String name() {
        return "query_price_trend";
    }

    @Override
    public String label() {
        return "价格趋势";
    }

    @Override
    public String description() {
        return "按物资名称查询近 N 个月的价格走势（供应商报价与采购订单实际价）。用户问「价格走势/最近采购价/历史价格」时使用。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("materialName", Map.of("type", "string", "description", "物资名称，支持部分匹配"));
        properties.put("months", Map.of("type", "integer", "description", "回看月数，默认3"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("materialName"));
        return schema;
    }

    @Override
    public boolean allowedFor(ToolContext ctx) {
        return ctx.hasRole("PURCHASER") || ctx.hasRole("FINANCE") || ctx.hasRole("DEPT_MANAGER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String materialName = QueryStockTool.str(args, "materialName");
        int months = Math.min(24, Math.max(1, QueryStockTool.intArg(args, "months", 3)));
        LocalDate since = LocalDate.now().minusMonths(months);

        List<Material> mats = lookup.findMaterials(materialName);
        if (mats.isEmpty()) {
            return ToolResult.of("未找到名称包含「" + materialName + "」的物资", List.of(), "0行");
        }
        if (mats.size() > 3) {
            List<Map<String, Object>> cand = new ArrayList<>();
            for (Material m : mats) {
                cand.add(Map.of("materialCode", m.getMaterialCode(), "materialName", m.getMaterialName()));
            }
            return ToolResult.of("匹配到多件物资，请用户澄清", cand, "候选" + mats.size() + "件");
        }
        Long materialId = mats.get(0).getId();

        List<Map<String, Object>> points = new ArrayList<>();
        List<SupplierProductPrice> priceRows = priceMapper.selectList(new LambdaQueryWrapper<SupplierProductPrice>()
                .eq(SupplierProductPrice::getMaterialId, materialId)
                .orderByAsc(SupplierProductPrice::getEffectiveFrom));
        for (SupplierProductPrice p : priceRows) {
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("date", p.getEffectiveFrom());
            pt.put("source", "供应商报价");
            pt.put("taxRate", p.getTaxRate());
            pt.put("taxInclusivePrice", p.getTaxInclusivePrice());
            pt.put("taxExclusivePrice", p.getTaxExclusivePrice());
            points.add(pt);
        }
        List<PurchaseOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getMaterialId, materialId));
        for (PurchaseOrderItem item : items) {
            PurchaseOrder order = item.getOrderId() == null ? null : orderMapper.selectById(item.getOrderId());
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("date", order != null ? order.getOrderDate() : null);
            pt.put("source", "采购订单");
            pt.put("orderNo", order != null ? order.getOrderNo() : null);
            pt.put("taxRate", item.getTaxRate());
            pt.put("taxInclusivePrice", item.getTaxInclusivePrice());
            pt.put("taxExclusivePrice", item.getTaxExclusivePrice());
            points.add(pt);
        }

        List<Map<String, Object>> filtered = points.stream()
                .filter(pt -> pt.get("date") != null)
                .filter(pt -> toLocalDate(pt.get("date")).isAfter(since) || toLocalDate(pt.get("date")).isEqual(since))
                .sorted(Comparator.comparing(pt -> toLocalDate(pt.get("date"))))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>(
                filtered.subList(Math.max(0, filtered.size() - MAX_POINTS), filtered.size()));

        String digest = rows.isEmpty() ? "无价格点"
                : "共" + rows.size() + "点，最新含税价" + latestPrice(rows);
        return ToolResult.of("近 " + months + " 个月共 " + rows.size() + " 个价格点", rows, digest);
    }

    private static String latestPrice(List<Map<String, Object>> rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            Object v = rows.get(i).get("taxInclusivePrice");
            if (v instanceof BigDecimal bd) {
                return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
            if (v instanceof Number n) {
                return String.valueOf(n);
            }
        }
        return "无";
    }

    private static LocalDate toLocalDate(Object v) {
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof java.time.LocalDateTime dt) {
            return dt.toLocalDate();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(v).substring(0, 10));
    }
}
