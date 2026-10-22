package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.PurchaseOrder;
import com.relex.wuzi.domain.PurchaseOrderItem;
import com.relex.wuzi.domain.SupplierProductPrice;
import com.relex.wuzi.mapper.PurchaseOrderItemMapper;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.SupplierProductPriceMapper;
import com.relex.wuzi.service.TraceScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购价格趋势（二期32简化）：supplier_product_price 与 purchase_order_item 历史价序列。
 */
@RestController
@RequestMapping("/api/price-trends")
@RequiredArgsConstructor
public class PriceTrendController {

    private final SupplierProductPriceMapper priceMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PurchaseOrderMapper orderMapper;

    @GetMapping
    public R<Map<String, Object>> trend(@RequestParam(required = false) Long materialId,
                                        @RequestParam(required = false) Long supplierProductId,
                                        @RequestParam(required = false) Boolean hidePrice,
                                        @RequestAttribute(value = "roles", required = false) List<String> roles) {
        if (materialId == null && supplierProductId == null) {
            throw new BizException("materialId 或 supplierProductId 至少提供一个");
        }
        boolean hide = TraceScanService.shouldHidePrice(hidePrice, roles);

        List<SupplierProductPrice> priceRows = priceMapper.selectList(new LambdaQueryWrapper<SupplierProductPrice>()
                .eq(materialId != null, SupplierProductPrice::getMaterialId, materialId)
                .eq(supplierProductId != null, SupplierProductPrice::getSupplierProductId, supplierProductId)
                .eq(SupplierProductPrice::getIsDeleted, 0)
                .orderByAsc(SupplierProductPrice::getEffectiveFrom)
                .orderByAsc(SupplierProductPrice::getId));

        List<PurchaseOrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(materialId != null, PurchaseOrderItem::getMaterialId, materialId)
                .eq(supplierProductId != null, PurchaseOrderItem::getSupplierProductId, supplierProductId)
                .orderByAsc(PurchaseOrderItem::getId));

        List<Map<String, Object>> series = new ArrayList<>();
        for (SupplierProductPrice p : priceRows) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", p.getEffectiveFrom());
            point.put("source", "SUPPLIER_PRICE");
            point.put("supplierProductId", p.getSupplierProductId());
            point.put("supplierId", p.getSupplierId());
            point.put("materialId", p.getMaterialId());
            point.put("taxRate", p.getTaxRate());
            if (!hide) {
                point.put("taxInclusivePrice", p.getTaxInclusivePrice());
                point.put("taxExclusivePrice", p.getTaxExclusivePrice());
            }
            series.add(point);
        }
        for (PurchaseOrderItem item : orderItems) {
            PurchaseOrder order = item.getOrderId() == null ? null : orderMapper.selectById(item.getOrderId());
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", order != null ? order.getOrderDate() : null);
            point.put("source", "PURCHASE_ORDER");
            point.put("orderId", item.getOrderId());
            point.put("orderNo", order != null ? order.getOrderNo() : null);
            point.put("supplierId", order != null ? order.getSupplierId() : null);
            point.put("supplierProductId", item.getSupplierProductId());
            point.put("materialId", item.getMaterialId());
            point.put("taxRate", item.getTaxRate());
            if (!hide) {
                point.put("taxInclusivePrice", item.getTaxInclusivePrice());
                point.put("taxExclusivePrice", item.getTaxExclusivePrice());
            }
            series.add(point);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("materialId", materialId);
        data.put("supplierProductId", supplierProductId);
        data.put("hidePrice", hide);
        data.put("points", series);
        data.put("count", series.size());
        return R.ok(data);
    }
}
