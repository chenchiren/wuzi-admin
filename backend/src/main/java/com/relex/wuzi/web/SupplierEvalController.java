package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.PurchaseOrder;
import com.relex.wuzi.domain.PurchaseReturn;
import com.relex.wuzi.domain.Supplier;
import com.relex.wuzi.domain.SupplierProduct;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.PurchaseReturnMapper;
import com.relex.wuzi.mapper.SupplierMapper;
import com.relex.wuzi.mapper.SupplierProductMapper;
import com.relex.wuzi.service.TraceScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商评价摘要（二期33简化）：采购金额 / 订单数 / 退货次数。
 */
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierEvalController {

    private final SupplierMapper supplierMapper;
    private final SupplierProductMapper supplierProductMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;

    @GetMapping("/{id}/summary")
    public R<Map<String, Object>> summary(@PathVariable Long id,
                                          @RequestParam(required = false) Boolean hidePrice,
                                          @RequestAttribute(value = "roles", required = false) List<String> roles) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null || (supplier.getIsDeleted() != null && supplier.getIsDeleted() == 1)) {
            throw new BizException("供应商不存在");
        }
        boolean hide = TraceScanService.shouldHidePrice(hidePrice, roles);

        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getSupplierId, id)
                .eq(PurchaseOrder::getIsDeleted, 0));
        List<PurchaseReturn> returns = purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getSupplierId, id)
                .eq(PurchaseReturn::getIsDeleted, 0));
        long productCount = supplierProductMapper.selectCount(new LambdaQueryWrapper<SupplierProduct>()
                .eq(SupplierProduct::getSupplierId, id)
                .eq(SupplierProduct::getIsDeleted, 0));

        BigDecimal purchaseAmount = BigDecimal.ZERO;
        int completedOrders = 0;
        for (PurchaseOrder order : orders) {
            if (order.getTaxInclusiveAmount() != null) {
                purchaseAmount = purchaseAmount.add(order.getTaxInclusiveAmount());
            }
            if ("RECEIVED".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) {
                completedOrders++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("supplierId", supplier.getId());
        summary.put("supplierCode", supplier.getSupplierCode());
        summary.put("supplierName", supplier.getSupplierName());
        summary.put("supplierType", supplier.getSupplierType());
        summary.put("coopStatus", supplier.getCoopStatus());
        summary.put("orderCount", orders.size());
        summary.put("completedOrderCount", completedOrders);
        summary.put("returnCount", returns.size());
        summary.put("productCount", productCount);
        summary.put("hidePrice", hide);
        if (!hide) {
            summary.put("purchaseAmount", purchaseAmount.setScale(2, RoundingMode.HALF_UP));
            summary.put("returnAmount", returns.stream()
                    .map(r -> r.getTaxInclAmount() == null ? BigDecimal.ZERO : r.getTaxInclAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP));
            summary.put("avgOrderAmount", orders.isEmpty() ? BigDecimal.ZERO
                    : purchaseAmount.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("summary", summary);
        return R.ok(data);
    }
}
