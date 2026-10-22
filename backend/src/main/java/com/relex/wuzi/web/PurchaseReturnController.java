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

/**
 * 采购退货：退货申请/确认。
 * 金额一律存正数，amount_sign=-1；汇总金额 = SUM(行金额 * amount_sign)。
 */
@RestController
@RequestMapping("/api/purchase/returns")
@RequiredArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnMapper returnMapper;
    private final PurchaseReturnItemMapper returnItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final MaterialMapper materialMapper;
    private final ApprovalService approvalService;
    private final FlowApprovalService flowApprovalService;
    private final PriceTaxCalculator calculator;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    private static final int AMOUNT_SIGN = -1;

    @Data
    public static class ReturnLine {
        private Long purchaseOrderItemId;
        private Long materialId;
        private BigDecimal returnQty;
        private BigDecimal taxInclusivePrice;
        private BigDecimal taxRate;
        private String taxCode;
    }

    @Data
    public static class CreateReq {
        private Long orderId;
        private Long supplierId;
        private String reason;
        private String remark;
        private List<ReturnLine> lines;
    }

    @GetMapping
    public R<PageResult<PurchaseReturn>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String status) {
        LambdaQueryWrapper<PurchaseReturn> qw = new LambdaQueryWrapper<PurchaseReturn>()
                .eq(status != null && !status.isBlank(), PurchaseReturn::getStatus, status)
                .orderByDesc(PurchaseReturn::getId);
        Page<PurchaseReturn> p = returnMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        PurchaseReturn ret = require(id);
        List<PurchaseReturnItem> items = returnItemMapper.selectList(new LambdaQueryWrapper<PurchaseReturnItem>()
                .eq(PurchaseReturnItem::getReturnId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("return", ret);
        map.put("items", items);
        map.put("signedAmount", signedSum(items));
        return R.ok(map);
    }

    /** 创建退货申请（DRAFT）。行金额存正数，amount_sign=-1 */
    @RequirePerm("po:return:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req) {
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new BizException("退货明细不能为空");
        }
        Long supplierId = req.getSupplierId();
        if (req.getOrderId() != null) {
            PurchaseOrder order = orderMapper.selectById(req.getOrderId());
            if (order == null) {
                throw new BizException("采购订单不存在");
            }
            if (supplierId == null) {
                supplierId = order.getSupplierId();
            }
        }
        if (supplierId == null) {
            throw new BizException("供应商不能为空");
        }

        PurchaseReturn ret = new PurchaseReturn();
        ret.setReturnNo(BillNoUtil.next("PR"));
        ret.setOrderId(req.getOrderId());
        ret.setSupplierId(supplierId);
        ret.setReason(req.getReason());
        ret.setStatus(ApprovalService.ST_DRAFT);
        ret.setAmountSign(AMOUNT_SIGN);
        ret.setTaxInclAmount(BigDecimal.ZERO);
        returnMapper.insert(ret);

        List<PurchaseReturnItem> items = new ArrayList<>();
        BigDecimal sumPositive = BigDecimal.ZERO;
        for (ReturnLine line : req.getLines()) {
            PurchaseReturnItem item = buildItem(ret.getId(), line);
            returnItemMapper.insert(item);
            items.add(item);
            sumPositive = sumPositive.add(nz(item.getTaxInclusiveAmount()));
        }
        // 主表金额为正数之和；对外汇总再乘 amount_sign
        ret.setTaxInclAmount(sumPositive);
        returnMapper.updateById(ret);

        Map<String, Object> result = new HashMap<>();
        result.put("return", ret);
        result.put("items", items);
        result.put("signedAmount", sumPositive.multiply(BigDecimal.valueOf(AMOUNT_SIGN)));
        return R.ok(result);
    }

    /** 提交退货申请进入审批 */
    @RequirePerm("po:return:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseReturn> submit(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        PurchaseReturn ret = require(id);
        if (!ApprovalService.ST_DRAFT.equals(ret.getStatus())
                && !ApprovalService.ST_REJECTED.equals(ret.getStatus())) {
            throw new BizException("当前状态不可提交");
        }
        List<PurchaseReturnItem> items = returnItemMapper.selectList(new LambdaQueryWrapper<PurchaseReturnItem>()
                .eq(PurchaseReturnItem::getReturnId, id));
        if (items.isEmpty()) {
            throw new BizException("退货明细不能为空");
        }
        final PurchaseReturn target = ret;
        flowApprovalService.start(ApprovalService.BIZ_RETURN, ApprovalService.BIZ_RETURN,
                id, ret.getReturnNo(), userId, "采购退货-" + ret.getReturnNo(),
                status -> {
                    target.setStatus(status);
                    returnMapper.updateById(target);
                });
        return R.ok(ret);
    }

    /** 审批通过后确认退货（COMPLETED） */
    @RequirePerm("po:return:confirm")
    @PostMapping("/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseReturn> confirm(@PathVariable Long id) {
        PurchaseReturn ret = require(id);
        if (!ApprovalService.ST_APPROVED.equals(ret.getStatus())) {
            throw new BizException("仅已通过的退货单可确认");
        }
        List<PurchaseReturnItem> items = returnItemMapper.selectList(new LambdaQueryWrapper<PurchaseReturnItem>()
                .eq(PurchaseReturnItem::getReturnId, id));
        for (PurchaseReturnItem item : items) {
            if (item.getPurchaseOrderItemId() == null) {
                continue;
            }
            PurchaseOrderItem oi = orderItemMapper.selectById(item.getPurchaseOrderItemId());
            if (oi != null) {
                BigDecimal reduce = nz(item.getReturnQty());
                oi.setQty(nz(oi.getQty()).subtract(reduce));
                orderItemMapper.updateById(oi);
            }
        }
        ret.setStatus(ApprovalService.ST_COMPLETED);
        returnMapper.updateById(ret);
        return R.ok(ret);
    }

    /** 作废（仅 DRAFT / REJECTED） */
    @RequirePerm("po:return:cancel")
    @PostMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseReturn> cancel(@PathVariable Long id) {
        PurchaseReturn ret = require(id);
        if (!ApprovalService.ST_DRAFT.equals(ret.getStatus())
                && !ApprovalService.ST_REJECTED.equals(ret.getStatus())) {
            throw new BizException("当前状态不可作废");
        }
        ret.setStatus(ApprovalService.ST_CANCELLED);
        returnMapper.updateById(ret);
        return R.ok(ret);
    }

    @RequirePerm("po:return:delete")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> delete(@PathVariable Long id) {
        PurchaseReturn ret = require(id);
        if (!ApprovalService.ST_DRAFT.equals(ret.getStatus())
                && !ApprovalService.ST_REJECTED.equals(ret.getStatus())
                && !ApprovalService.ST_CANCELLED.equals(ret.getStatus())) {
            throw new BizException("仅草稿/驳回/作废单可删除");
        }
        returnItemMapper.delete(new LambdaQueryWrapper<PurchaseReturnItem>()
                .eq(PurchaseReturnItem::getReturnId, id));
        returnMapper.deleteById(id);
        return R.ok();
    }

    private PurchaseReturnItem buildItem(Long returnId, ReturnLine line) {
        if (line.getReturnQty() == null || line.getReturnQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("退货数量必须大于0");
        }
        Long materialId = line.getMaterialId();
        BigDecimal price = line.getTaxInclusivePrice();
        BigDecimal rate = line.getTaxRate();
        String taxCode = line.getTaxCode();

        if (line.getPurchaseOrderItemId() != null) {
            PurchaseOrderItem oi = orderItemMapper.selectById(line.getPurchaseOrderItemId());
            if (oi == null) {
                throw new BizException("订单明细不存在: " + line.getPurchaseOrderItemId());
            }
            if (materialId == null) {
                materialId = oi.getMaterialId();
            }
            if (price == null) {
                price = oi.getTaxInclusivePrice();
            }
            if (rate == null) {
                rate = oi.getTaxRate();
            }
            if (taxCode == null) {
                taxCode = oi.getTaxCode();
            }
        }
        if (materialId == null) {
            throw new BizException("物资不能为空");
        }
        if (materialMapper.selectById(materialId) == null) {
            throw new BizException("物资不存在: " + materialId);
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("含税单价必须大于0");
        }
        if (rate == null) {
            rate = new BigDecimal("13");
        }

        PriceTaxCalculator.Line calc = calculator.fromInclusive(price, rate, line.getReturnQty());

        PurchaseReturnItem item = new PurchaseReturnItem();
        item.setReturnId(returnId);
        item.setPurchaseOrderItemId(line.getPurchaseOrderItemId());
        item.setMaterialId(materialId);
        item.setTaxCode(taxCode);
        item.setTaxRate(rate);
        item.setTaxInclusivePrice(calc.taxInclusivePrice);
        item.setTaxExclusivePrice(calc.taxExclusivePrice);
        item.setReturnQty(line.getReturnQty());
        // 金额存正数
        item.setTaxInclusiveAmount(calc.taxInclusiveAmount);
        item.setTaxExclusiveAmount(calc.taxExclAmount);
        item.setTaxAmount(calc.taxAmount);
        item.setAmountSign(AMOUNT_SIGN);
        return item;
    }

    /** 汇总：SUM(行含税金额 * amount_sign) */
    private BigDecimal signedSum(List<PurchaseReturnItem> items) {
        return items.stream()
                .map(it -> nz(it.getTaxInclusiveAmount())
                        .multiply(BigDecimal.valueOf(it.getAmountSign() != null ? it.getAmountSign() : AMOUNT_SIGN)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PurchaseReturn require(Long id) {
        PurchaseReturn ret = returnMapper.selectById(id);
        if (ret == null) {
            throw new BizException("退货单不存在");
        }
        return ret;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
