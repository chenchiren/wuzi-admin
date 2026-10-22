package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.BillNoUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import com.relex.wuzi.service.ApprovalService;
import com.relex.wuzi.service.FlowApprovalService;
import com.relex.wuzi.service.PriceTaxCalculator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/purchase/orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierProductMapper supplierProductMapper;
    private final PriceTaxCalculator calculator;
    private final FlowApprovalService flowApprovalService;
    private final TaxRateDictMapper taxRateDictMapper;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class CreateItem {
        private Long materialId;
        private Long supplierProductId;
        private BigDecimal qty;
        private BigDecimal taxInclusivePrice;
        /** 税种编码快照；手填税率时为空 */
        private String taxCode;
        private BigDecimal taxRate;
        private String unit;
    }

    @Data
    public static class CreateReq {
        private Long supplierId;
        private LocalDate expectArrivalDate;
        private String remark;
        private List<CreateItem> items;
    }

    @GetMapping
    public R<PageResult<PurchaseOrder>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PurchaseOrder> qw = new LambdaQueryWrapper<PurchaseOrder>()
                .eq(status != null && !status.isBlank(), PurchaseOrder::getStatus, status)
                .orderByDesc(PurchaseOrder::getId);
        Page<PurchaseOrder> p = orderMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        List<PurchaseOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("order", order);
        map.put("items", items);
        return R.ok(map);
    }

    @RequirePerm("po:order:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req, @RequestAttribute("userId") Long userId) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("订单明细不能为空");
        }
        Supplier supplier = supplierMapper.selectById(req.getSupplierId());
        if (supplier == null) {
            throw new BizException("供应商不存在");
        }

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(BillNoUtil.next("PO"));
        order.setSupplierId(req.getSupplierId());
        order.setPurchaseUserId(userId);
        order.setOrderDate(LocalDate.now());
        order.setExpectArrivalDate(req.getExpectArrivalDate());
        order.setCurrency("CNY");
        order.setDefaultTaxRate(supplier.getDefaultTaxRate());
        order.setStatus("DRAFT");
        order.setRemark(req.getRemark());
        order.setCreatedBy(userId);
        orderMapper.insert(order);

        BigDecimal sumIncl = BigDecimal.ZERO;
        BigDecimal sumExcl = BigDecimal.ZERO;
        BigDecimal sumTax = BigDecimal.ZERO;
        Map<BigDecimal, Integer> rateCount = new TreeMap<>();
        List<PurchaseOrderItem> items = new ArrayList<>();

        for (CreateItem it : req.getItems()) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            BigDecimal price = it.getTaxInclusivePrice();
            BigDecimal rate = it.getTaxRate() != null ? it.getTaxRate() : supplier.getDefaultTaxRate();
            String taxCode = it.getTaxCode();
            String priceSource = "MANUAL";
            Long priceSourceId = null;
            if (it.getSupplierProductId() != null) {
                SupplierProduct sp = supplierProductMapper.selectById(it.getSupplierProductId());
                if (sp != null) {
                    if (price == null) {
                        price = sp.getTaxInclusivePrice();
                    }
                    if (it.getTaxRate() == null) {
                        rate = sp.getTaxRate();
                    }
                    if (taxCode == null || taxCode.isBlank()) {
                        taxCode = sp.getTaxCode();
                    }
                    priceSource = "PRODUCT";
                    priceSourceId = sp.getId();
                }
            }
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                price = material.getRefPrice();
                priceSource = "MATERIAL_REF";
            }
            if (taxCode == null || taxCode.isBlank()) {
                taxCode = findTaxCodeByRate(rate);
            }
            PriceTaxCalculator.Line line = calculator.fromInclusive(price, rate, it.getQty());

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setOrderId(order.getId());
            item.setMaterialId(it.getMaterialId());
            item.setSupplierProductId(it.getSupplierProductId());
            item.setTaxCode(taxCode);
            item.setTaxRate(rate);
            item.setTaxInclusivePrice(line.taxInclusivePrice);
            item.setTaxExclusivePrice(line.taxExclusivePrice);
            item.setTaxUnitAmount(line.taxUnitAmount);
            item.setQty(it.getQty());
            item.setTaxInclusiveAmount(line.taxInclusiveAmount);
            item.setTaxExclusiveAmount(line.taxExclAmount);
            item.setTaxAmount(line.taxAmount);
            item.setCurrency("CNY");
            item.setUnit(it.getUnit() != null ? it.getUnit() : material.getUnit());
            item.setPriceSource(priceSource);
            item.setPriceSourceId(priceSourceId);
            item.setTaxCalcFlag("AUTO");
            itemMapper.insert(item);
            items.add(item);

            sumIncl = sumIncl.add(line.taxInclusiveAmount);
            sumExcl = sumExcl.add(line.taxExclAmount);
            sumTax = sumTax.add(line.taxAmount);
            rateCount.merge(rate, 1, Integer::sum);
        }

        order.setTaxInclusiveAmount(sumIncl);
        order.setTaxExclusiveAmount(sumExcl);
        order.setTaxAmount(sumTax);
        StringBuilder sb = new StringBuilder();
        rateCount.forEach((rate, c) -> {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(rate).append("%：").append(c).append(" 行");
        });
        order.setTaxRateSummary(sb.toString());
        orderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", items);
        return R.ok(result);
    }

    /** 税率与字典一致时补税码；手填非标税率保持 null */
    private String findTaxCodeByRate(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        return taxRateDictMapper.selectList(new LambdaQueryWrapper<TaxRateDict>()
                        .eq(TaxRateDict::getIsDeleted, 0)
                        .eq(TaxRateDict::getIsEnabled, 1)
                        .eq(TaxRateDict::getTaxRate, rate)
                        .last("limit 1")).stream()
                .map(TaxRateDict::getTaxCode)
                .findFirst()
                .orElse(null);
    }

    /** 提交采购订单进入审批流 */
    @RequirePerm("po:order:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseOrder> submit(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BizException("当前状态不可提交: " + order.getStatus());
        }
        List<PurchaseOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, id));
        if (items.isEmpty()) {
            throw new BizException("订单明细不能为空");
        }
        flowApprovalService.start(ApprovalService.BIZ_ORDER, ApprovalService.BIZ_ORDER,
                id, order.getOrderNo(), userId, "采购订单-" + order.getOrderNo(),
                status -> {
                    order.setStatus(status);
                    orderMapper.updateById(order);
                });
        return R.ok(order);
    }

    /** 作废订单（仅草稿/驳回） */
    @RequirePerm("po:order:cancel")
    @PostMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseOrder> cancel(@PathVariable Long id) {
        PurchaseOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BizException("当前状态不可作废");
        }
        order.setStatus("CANCELLED");
        orderMapper.updateById(order);
        return R.ok(order);
    }
}
