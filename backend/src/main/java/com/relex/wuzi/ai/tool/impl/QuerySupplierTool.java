package com.relex.wuzi.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.ai.service.AiLookupService;
import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.domain.PurchaseOrder;
import com.relex.wuzi.domain.PurchaseReturn;
import com.relex.wuzi.domain.Supplier;
import com.relex.wuzi.domain.SupplierProduct;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.PurchaseReturnMapper;
import com.relex.wuzi.mapper.SupplierProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商查询：基本信息 + 合作概要（订单数/退货数/采购额）。
 * 可见角色：PURCHASER / FINANCE（ADMIN 自动放行）。
 */
@Component
@RequiredArgsConstructor
public class QuerySupplierTool implements AiTool {

    private static final int MAX_ROWS = 3;

    private final AiLookupService lookup;
    private final SupplierProductMapper supplierProductMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;

    @Override
    public String name() {
        return "query_supplier";
    }

    @Override
    public String label() {
        return "查供应商";
    }

    @Override
    public String description() {
        return "按名称查供应商：联系人、电话、合作状态、默认税率、订单数/采购额概要。用户问「XX供应商/供应商联系方式」时使用。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("supplierName", Map.of("type", "string", "description", "供应商名称，支持部分匹配"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("supplierName"));
        return schema;
    }

    @Override
    public boolean allowedFor(ToolContext ctx) {
        return ctx.hasRole("PURCHASER") || ctx.hasRole("FINANCE");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String supplierName = QueryStockTool.str(args, "supplierName");
        List<Supplier> suppliers = lookup.findSuppliers(supplierName);
        if (suppliers.isEmpty()) {
            return ToolResult.of("未找到名称包含「" + supplierName + "」的供应商", List.of(), "0家");
        }
        if (suppliers.size() > MAX_ROWS) {
            List<Map<String, Object>> cand = new ArrayList<>();
            for (Supplier s : suppliers) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("supplierCode", s.getSupplierCode());
                row.put("supplierName", s.getSupplierName());
                row.put("coopStatus", s.getCoopStatus());
                cand.add(row);
            }
            return ToolResult.of("匹配到 " + suppliers.size() + " 家供应商，请用户澄清", cand, "候选" + suppliers.size() + "家");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Supplier s : suppliers) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("supplierCode", s.getSupplierCode());
            row.put("supplierName", s.getSupplierName());
            row.put("supplierType", s.getSupplierType());
            row.put("coopStatus", s.getCoopStatus());
            row.put("contactName", s.getContactName());
            row.put("contactPhone", s.getContactPhone());
            row.put("email", s.getEmail());
            row.put("defaultTaxRate", s.getDefaultTaxRate());

            List<PurchaseOrder> orders = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getSupplierId, s.getId()));
            List<PurchaseReturn> returns = purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturn>()
                    .eq(PurchaseReturn::getSupplierId, s.getId()));
            long productCount = supplierProductMapper.selectCount(new LambdaQueryWrapper<SupplierProduct>()
                    .eq(SupplierProduct::getSupplierId, s.getId()));

            BigDecimal amount = BigDecimal.ZERO;
            int completed = 0;
            for (PurchaseOrder o : orders) {
                if (o.getTaxInclusiveAmount() != null) {
                    amount = amount.add(o.getTaxInclusiveAmount());
                }
                if ("RECEIVED".equals(o.getStatus()) || "COMPLETED".equals(o.getStatus())) {
                    completed++;
                }
            }
            row.put("orderCount", orders.size());
            row.put("completedOrderCount", completed);
            row.put("returnCount", returns.size());
            row.put("productCount", productCount);
            row.put("purchaseAmount", amount.setScale(2, RoundingMode.HALF_UP));
            rows.add(row);
        }
        return ToolResult.of("查到 " + rows.size() + " 家供应商", rows,
                "供应商" + rows.get(0).get("supplierName") + " 订单" + rows.get(0).get("orderCount") + "笔");
    }
}
