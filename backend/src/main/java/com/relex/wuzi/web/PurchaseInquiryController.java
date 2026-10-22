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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 采购询价：对已通过申请生成询价、录入报价（PriceTaxCalculator）、选定供应商
 */
@RestController
@RequestMapping("/api/purchase/inquiries")
@RequiredArgsConstructor
public class PurchaseInquiryController {

    private final PurchaseInquiryMapper inquiryMapper;
    private final PurchaseInquiryItemMapper inquiryItemMapper;
    private final PurchaseApplyMapper applyMapper;
    private final PurchaseApplyItemMapper applyItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final TaxPriceAdjustLogMapper adjustLogMapper;
    private final PriceTaxCalculator calculator;
    private final ApprovalService approvalService;
    private final FlowApprovalService flowApprovalService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class QuoteLine {
        private Long materialId;
        private BigDecimal qty;
        /** 含税报价单价 */
        private BigDecimal taxInclusivePrice;
        private BigDecimal taxRate;
        private String taxCode;
        private String unit;
        private String remark;
    }

    @Data
    public static class QuoteReq {
        private Long supplierId;
        private List<QuoteLine> lines;
    }

    @Data
    public static class SelectReq {
        private Long supplierId;
        /** true 时同步生成采购订单草稿 */
        private Boolean createOrder;
        private LocalDate expectArrivalDate;
        private String remark;
    }

    @Data
    public static class SubmitReq {
        private String remark;
    }

    @Data
    public static class ApproveReq {
        private String comment;
    }

    @GetMapping
    public R<PageResult<PurchaseInquiry>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PurchaseInquiry> qw = new LambdaQueryWrapper<PurchaseInquiry>()
                .eq(status != null && !status.isBlank(), PurchaseInquiry::getStatus, status)
                .orderByDesc(PurchaseInquiry::getId);
        Page<PurchaseInquiry> p = inquiryMapper.selectPage(new Page<>(page, size), qw);
        fillApplyNo(p.getRecords());
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    private void fillApplyNo(List<PurchaseInquiry> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> applyIds = list.stream()
                .map(PurchaseInquiry::getApplyId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (applyIds.isEmpty()) return;
        Map<Long, String> applyNoMap = applyMapper.selectBatchIds(applyIds).stream()
                .collect(java.util.stream.Collectors.toMap(PurchaseApply::getId, PurchaseApply::getApplyNo, (a, b) -> a));
        for (PurchaseInquiry inquiry : list) {
            if (inquiry.getApplyId() != null) {
                inquiry.setApplyNo(applyNoMap.get(inquiry.getApplyId()));
            }
        }
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        PurchaseInquiry inquiry = require(id);
        List<PurchaseInquiryItem> items = inquiryItemMapper.selectList(new LambdaQueryWrapper<PurchaseInquiryItem>()
                .eq(PurchaseInquiryItem::getInquiryId, id)
                .orderByAsc(PurchaseInquiryItem::getSupplierId)
                .orderByAsc(PurchaseInquiryItem::getMaterialId));
        Map<String, Object> map = new HashMap<>();
        map.put("inquiry", inquiry);
        map.put("items", items);
        return R.ok(map);
    }

    /** 对已通过的采购申请生成询价单，明细行从申请复制（待报价） */
    @RequirePerm("po:inquiry:create")
    @PostMapping("/from-apply/{applyId}")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> createFromApply(@PathVariable Long applyId) {
        PurchaseApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BizException("采购申请不存在");
        }
        if (!ApprovalService.ST_APPROVED.equals(apply.getStatus())) {
            throw new BizException("仅已通过的申请可生成询价");
        }
        List<PurchaseApplyItem> applyItems = applyItemMapper.selectList(new LambdaQueryWrapper<PurchaseApplyItem>()
                .eq(PurchaseApplyItem::getApplyId, applyId));
        if (applyItems.isEmpty()) {
            throw new BizException("申请明细为空");
        }

        PurchaseInquiry inquiry = new PurchaseInquiry();
        inquiry.setInquiryNo(BillNoUtil.next("IQ"));
        inquiry.setApplyId(applyId);
        inquiry.setTitle("采购询价-" + apply.getApplyNo());
        inquiry.setInquireTime(LocalDateTime.now());
        inquiry.setStatus(ApprovalService.ST_DRAFT);
        inquiry.setRemark(apply.getRemark());
        inquiryMapper.insert(inquiry);

        List<PurchaseInquiryItem> items = new ArrayList<>();
        for (PurchaseApplyItem ai : applyItems) {
            PurchaseInquiryItem item = new PurchaseInquiryItem();
            item.setInquiryId(inquiry.getId());
            item.setMaterialId(ai.getMaterialId());
            item.setQty(ai.getQty());
            item.setUnit(ai.getUnit());
            item.setTaxCode(ai.getTaxCode());
            item.setTaxRate(ai.getTaxRate());
            item.setIsSelected(0);
            item.setRemark(ai.getRemark());
            inquiryItemMapper.insert(item);
            items.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("inquiry", inquiry);
        result.put("items", items);
        return R.ok(result);
    }

    /** 提交审批（独立审批：询价须先提交、通过后方可选定供应商） */
    @RequirePerm("po:inquiry:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseInquiry> submit(@PathVariable Long id, @RequestBody(required = false) SubmitReq req,
                                     @RequestAttribute("userId") Long userId) {
        PurchaseInquiry inquiry = requireEditable(id);
        List<PurchaseInquiryItem> items = inquiryItemMapper.selectList(new LambdaQueryWrapper<PurchaseInquiryItem>()
                .eq(PurchaseInquiryItem::getInquiryId, id));
        if (items.isEmpty()) {
            throw new BizException("询价明细不能为空，请先补全");
        }
        long quoted = items.stream().filter(it -> it.getSupplierId() != null).count();
        if (quoted == 0) {
            throw new BizException("请先录入供应商报价再提交审批");
        }
        if (req != null && req.getRemark() != null) {
            inquiry.setRemark(req.getRemark());
            inquiryMapper.updateById(inquiry);
        }
        flowApprovalService.start(ApprovalService.BIZ_INQUIRY, ApprovalService.BIZ_INQUIRY,
                id, inquiry.getInquiryNo(), userId,
                "采购询价-" + (inquiry.getTitle() != null ? inquiry.getTitle() : inquiry.getInquiryNo()),
                status -> {
                    inquiry.setStatus(status);
                    inquiryMapper.updateById(inquiry);
                });
        return R.ok(inquiry);
    }

    /** 作废（仅 DRAFT / REJECTED） */
    @RequirePerm("po:inquiry:cancel")
    @PostMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseInquiry> cancel(@PathVariable Long id) {
        PurchaseInquiry inquiry = requireEditable(id);
        inquiry.setStatus(ApprovalService.ST_CANCELLED);
        inquiryMapper.updateById(inquiry);
        return R.ok(inquiry);
    }

    /** 直接审批通过（也可走 /api/approvals 统一审批） */
    @RequirePerm("po:inquiry:approve")
    @PostMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseInquiry> approve(@PathVariable Long id, @RequestBody(required = false) ApproveReq req,
                                      @RequestAttribute("userId") Long userId) {
        PurchaseInquiry inquiry = require(id);
        if (!ApprovalService.ST_PENDING.equals(inquiry.getStatus())) {
            throw new BizException("当前状态不可审批");
        }
        ApprovalInstance inst = flowApprovalService.getByBizIfPresent(ApprovalService.BIZ_INQUIRY, id);
        String comment = req != null ? req.getComment() : null;
        if (inst != null && flowApprovalService.hasFlow(inst.getId())) {
            flowApprovalService.approve(inst.getId(), userId, comment,
                    status -> applyInquiryStatus(inquiry, status, userId));
        } else if (inst != null) {
            approvalService.approve(inst.getId(), userId, comment,
                    status -> applyInquiryStatus(inquiry, status, userId));
        } else {
            applyInquiryStatus(inquiry, ApprovalService.ST_APPROVED, userId);
        }
        return R.ok(inquiryMapper.selectById(id));
    }

    /** 直接审批驳回 */
    @RequirePerm("po:inquiry:reject")
    @PostMapping("/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseInquiry> reject(@PathVariable Long id, @RequestBody(required = false) ApproveReq req,
                                     @RequestAttribute("userId") Long userId) {
        PurchaseInquiry inquiry = require(id);
        if (!ApprovalService.ST_PENDING.equals(inquiry.getStatus())) {
            throw new BizException("当前状态不可审批");
        }
        ApprovalInstance inst = flowApprovalService.getByBizIfPresent(ApprovalService.BIZ_INQUIRY, id);
        String comment = req != null ? req.getComment() : null;
        if (inst != null && flowApprovalService.hasFlow(inst.getId())) {
            flowApprovalService.reject(inst.getId(), userId, comment,
                    status -> applyInquiryStatus(inquiry, status, userId));
        } else if (inst != null) {
            approvalService.reject(inst.getId(), userId, comment,
                    status -> applyInquiryStatus(inquiry, status, userId));
        } else {
            applyInquiryStatus(inquiry, ApprovalService.ST_REJECTED, userId);
        }
        return R.ok(inquiryMapper.selectById(id));
    }

    private void applyInquiryStatus(PurchaseInquiry inquiry, String status, Long operatorId) {
        inquiry.setStatus(status);
        if (ApprovalService.ST_APPROVED.equals(status)) {
            inquiry.setApprovedBy(operatorId);
            inquiry.setApprovedAt(LocalDateTime.now());
        }
        inquiryMapper.updateById(inquiry);
    }

    /** 录入某供应商报价（价格走 PriceTaxCalculator） */
    @RequirePerm("po:inquiry:quote")
    @PostMapping("/{id}/quotes")
    @Transactional(rollbackFor = Exception.class)
    public R<List<PurchaseInquiryItem>> enterQuotes(@PathVariable Long id,
                                                    @RequestBody QuoteReq req,
                                                    @RequestAttribute("userId") Long userId) {
        PurchaseInquiry inquiry = requireOpen(id);
        if (req.getSupplierId() == null) {
            throw new BizException("供应商不能为空");
        }
        Supplier supplier = supplierMapper.selectById(req.getSupplierId());
        if (supplier == null) {
            throw new BizException("供应商不存在");
        }
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new BizException("报价明细不能为空");
        }

        // 同一供应商重复录入时覆盖旧行
        inquiryItemMapper.delete(new LambdaQueryWrapper<PurchaseInquiryItem>()
                .eq(PurchaseInquiryItem::getInquiryId, id)
                .eq(PurchaseInquiryItem::getSupplierId, req.getSupplierId()));

        List<PurchaseInquiryItem> result = new ArrayList<>();
        for (QuoteLine line : req.getLines()) {
            Material material = materialMapper.selectById(line.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + line.getMaterialId());
            }
            BigDecimal qty = line.getQty();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("数量必须大于0");
            }
            BigDecimal price = line.getTaxInclusivePrice();
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("含税报价必须大于0");
            }
            BigDecimal rate = line.getTaxRate() != null ? line.getTaxRate()
                    : (supplier.getDefaultTaxRate() != null ? supplier.getDefaultTaxRate() : new BigDecimal("13"));
            PriceTaxCalculator.Line calc = calculator.fromInclusive(price, rate, qty);

            PurchaseInquiryItem item = new PurchaseInquiryItem();
            item.setInquiryId(id);
            item.setMaterialId(line.getMaterialId());
            item.setSupplierId(req.getSupplierId());
            item.setQty(qty);
            item.setUnit(line.getUnit() != null ? line.getUnit() : material.getUnit());
            item.setTaxCode(line.getTaxCode());
            item.setTaxRate(rate);
            item.setTaxInclusivePrice(calc.taxInclusivePrice);
            item.setTaxExclusivePrice(calc.taxExclusivePrice);
            item.setTaxUnitAmount(calc.taxUnitAmount);
            item.setTaxInclusiveAmount(calc.taxInclusiveAmount);
            item.setTaxExclusiveAmount(calc.taxExclAmount);
            item.setTaxAmount(calc.taxAmount);
            item.setIsSelected(0);
            item.setPriceSource("QUOTE");
            item.setRemark(line.getRemark());
            inquiryItemMapper.insert(item);
            result.add(item);

            logPriceAdjust("PURCHASE_INQUIRY", id, "taxInclusivePrice",
                    null, calc.taxInclusivePrice.toPlainString(), "QUOTE", userId, "询价报价录入");
        }
        return R.ok(result);
    }

    /** 选定供应商（须已审批通过），可选同步生成采购订单草稿 */
    @RequirePerm("po:inquiry:select")
    @PostMapping("/{id}/select-supplier")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> selectSupplier(@PathVariable Long id,
                                                 @RequestBody SelectReq req,
                                                 @RequestAttribute("userId") Long userId) {
        PurchaseInquiry inquiry = requireApproved(id);
        if (req.getSupplierId() == null) {
            throw new BizException("供应商不能为空");
        }
        List<PurchaseInquiryItem> quoted = inquiryItemMapper.selectList(new LambdaQueryWrapper<PurchaseInquiryItem>()
                .eq(PurchaseInquiryItem::getInquiryId, id)
                .eq(PurchaseInquiryItem::getSupplierId, req.getSupplierId()));
        if (quoted.isEmpty()) {
            throw new BizException("该供应商无报价明细");
        }

        inquiryItemMapper.selectList(new LambdaQueryWrapper<PurchaseInquiryItem>()
                        .eq(PurchaseInquiryItem::getInquiryId, id))
                .forEach(it -> {
                    it.setIsSelected(req.getSupplierId().equals(it.getSupplierId()) ? 1 : 0);
                    inquiryItemMapper.updateById(it);
                });

        inquiry.setSelectedSupplierId(req.getSupplierId());
        // 审批通过后选定供应商，状态保持 APPROVED（完成询价时置 COMPLETED）
        if (req.getRemark() != null) {
            inquiry.setRemark(req.getRemark());
        }
        inquiryMapper.updateById(inquiry);

        Map<String, Object> result = new HashMap<>();
        result.put("inquiry", inquiry);
        result.put("items", quoted);

        if (Boolean.TRUE.equals(req.getCreateOrder())) {
            PurchaseOrder order = createOrderFromInquiry(inquiry, quoted, req, userId);
            result.put("order", order);
        }
        return R.ok(result);
    }

    /** 询价完成 */
    @RequirePerm("po:inquiry:complete")
    @PostMapping("/{id}/complete")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseInquiry> complete(@PathVariable Long id) {
        PurchaseInquiry inquiry = require(id);
        if (inquiry.getSelectedSupplierId() == null) {
            throw new BizException("请先选定供应商");
        }
        if (!ApprovalService.ST_APPROVED.equals(inquiry.getStatus())) {
            throw new BizException("当前状态不可完成");
        }
        inquiry.setStatus(ApprovalService.ST_COMPLETED);
        inquiryMapper.updateById(inquiry);
        return R.ok(inquiry);
    }

    private PurchaseOrder createOrderFromInquiry(PurchaseInquiry inquiry, List<PurchaseInquiryItem> quoted,
                                                 SelectReq req, Long userId) {
        Supplier supplier = supplierMapper.selectById(req.getSupplierId());
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(BillNoUtil.next("PO"));
        order.setSupplierId(req.getSupplierId());
        order.setApplyId(inquiry.getApplyId());
        order.setInquiryId(inquiry.getId());
        order.setInquiryNo(inquiry.getInquiryNo());
        order.setPurchaseUserId(userId);
        order.setOrderDate(LocalDate.now());
        order.setExpectArrivalDate(req.getExpectArrivalDate());
        order.setCurrency("CNY");
        order.setDefaultTaxRate(supplier != null ? supplier.getDefaultTaxRate() : null);
        order.setStatus(ApprovalService.ST_DRAFT);
        order.setRemark("由询价单 " + inquiry.getInquiryNo() + " 生成");
        order.setCreatedBy(userId);
        orderMapper.insert(order);

        BigDecimal sumIncl = BigDecimal.ZERO;
        BigDecimal sumExcl = BigDecimal.ZERO;
        BigDecimal sumTax = BigDecimal.ZERO;
        Map<BigDecimal, Integer> rateCount = new TreeMap<>();

        for (PurchaseInquiryItem q : quoted) {
            PriceTaxCalculator.Line line = calculator.fromInclusive(
                    q.getTaxInclusivePrice(), q.getTaxRate(), q.getQty());

            PurchaseOrderItem oi = new PurchaseOrderItem();
            oi.setOrderId(order.getId());
            oi.setMaterialId(q.getMaterialId());
            oi.setTaxCode(q.getTaxCode());
            oi.setTaxRate(q.getTaxRate());
            oi.setTaxInclusivePrice(line.taxInclusivePrice);
            oi.setTaxExclusivePrice(line.taxExclusivePrice);
            oi.setTaxUnitAmount(line.taxUnitAmount);
            oi.setQty(q.getQty());
            oi.setTaxInclusiveAmount(line.taxInclusiveAmount);
            oi.setTaxExclusiveAmount(line.taxExclAmount);
            oi.setTaxAmount(line.taxAmount);
            oi.setCurrency("CNY");
            oi.setUnit(q.getUnit());
            oi.setPriceSource("INQUIRY");
            oi.setPriceSourceId(q.getId());
            oi.setExpectedArrivalDate(req.getExpectArrivalDate());
            oi.setTaxCalcFlag("AUTO");
            orderItemMapper.insert(oi);

            sumIncl = sumIncl.add(line.taxInclusiveAmount);
            sumExcl = sumExcl.add(line.taxExclAmount);
            sumTax = sumTax.add(line.taxAmount);
            rateCount.merge(q.getTaxRate(), 1, Integer::sum);
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
        return order;
    }

    private void logPriceAdjust(String bizType, Long bizId, String field, String oldVal, String newVal,
                                String adjustType, Long operatorId, String reason) {
        TaxPriceAdjustLog log = new TaxPriceAdjustLog();
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setFieldName(field);
        log.setOldValue(oldVal);
        log.setNewValue(newVal);
        log.setAdjustType(adjustType);
        log.setOperatorId(operatorId);
        log.setReason(reason);
        adjustLogMapper.insert(log);
    }

    private PurchaseInquiry require(Long id) {
        PurchaseInquiry inquiry = inquiryMapper.selectById(id);
        if (inquiry == null) {
            throw new BizException("询价单不存在");
        }
        return inquiry;
    }

    /** 可编辑（录入报价/提交/作废）：DRAFT 或 REJECTED */
    private PurchaseInquiry requireEditable(Long id) {
        PurchaseInquiry inquiry = require(id);
        if (!ApprovalService.ST_DRAFT.equals(inquiry.getStatus())
                && !ApprovalService.ST_REJECTED.equals(inquiry.getStatus())) {
            throw new BizException("当前状态不可操作询价");
        }
        return inquiry;
    }

    /** 录入报价：DRAFT / REJECTED（草稿或驳回后补录） */
    private PurchaseInquiry requireOpen(Long id) {
        return requireEditable(id);
    }

    /** 选定供应商：须审批通过 */
    private PurchaseInquiry requireApproved(Long id) {
        PurchaseInquiry inquiry = require(id);
        if (!ApprovalService.ST_APPROVED.equals(inquiry.getStatus())) {
            throw new BizException("请先提交询价并审批通过");
        }
        return inquiry;
    }
}
