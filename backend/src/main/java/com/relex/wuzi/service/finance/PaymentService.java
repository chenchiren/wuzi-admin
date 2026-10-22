package com.relex.wuzi.service.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BillNoUtil;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.ContractPaymentNode;
import com.relex.wuzi.domain.PaymentOrder;
import com.relex.wuzi.domain.PurchaseContract;
import com.relex.wuzi.mapper.ContractPaymentNodeMapper;
import com.relex.wuzi.mapper.PaymentOrderMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 付款单 / 应付账款：关联合同与结算，核销付款节点，统计应付账龄。
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_PENDING = "PENDING";
    public static final String ST_PAID = "PAID";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_CANCELLED = "CANCELLED";

    private final PaymentOrderMapper paymentMapper;
    private final ContractService contractService;
    private final BudgetService budgetService;
    private final ContractPaymentNodeMapper nodeMapper;

    @Data
    public static class CreateReq {
        private Long supplierId;
        private Long contractId;
        private Long settleId;
        private Long payNodeId;
        private Long budgetId;
        private BigDecimal amount;
        private String payMethod;
        private LocalDate payDate;
        private Long deptId;
        private String remark;
    }

    public PageResult<PaymentOrder> page(long page, long size, String status, Long supplierId, Long contractId) {
        Page<PaymentOrder> p = paymentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(status != null && !status.isBlank(), PaymentOrder::getStatus, status)
                        .eq(supplierId != null, PaymentOrder::getSupplierId, supplierId)
                        .eq(contractId != null, PaymentOrder::getContractId, contractId)
                        .orderByDesc(PaymentOrder::getId));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrder create(CreateReq req, Long userId) {
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("付款金额必须大于 0");
        }
        Long budgetId = req.getBudgetId();
        Long contractId = req.getContractId();
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (contractId != null) {
            PurchaseContract c = contractService.require(contractId);
            if (!"ACTIVE".equals(c.getStatus())) {
                throw new BizException("合同未执行，不可付款");
            }
            BigDecimal remain = c.getContractAmount().subtract(c.getPaidAmount() == null ? BigDecimal.ZERO : c.getPaidAmount());
            // 含在途未付
            BigDecimal inTransit = sumInTransit(contractId);
            if (amount.add(inTransit).compareTo(remain) > 0) {
                throw new BizException("付款超出合同剩余可付：剩余 "
                        + remain.setScale(2, RoundingMode.HALF_UP) + "，在途 " + inTransit.setScale(2, RoundingMode.HALF_UP));
            }
            if (budgetId == null) budgetId = c.getBudgetId();
            if (req.getSupplierId() == null) req.setSupplierId(c.getSupplierId());
        }
        PaymentOrder p = new PaymentOrder();
        p.setPaymentNo(BillNoUtil.next("FK-"));
        p.setSupplierId(req.getSupplierId());
        p.setContractId(contractId);
        p.setSettleId(req.getSettleId());
        p.setPayNodeId(req.getPayNodeId());
        p.setBudgetId(budgetId);
        p.setAmount(amount);
        p.setPayMethod(req.getPayMethod() == null || req.getPayMethod().isBlank() ? "BANK" : req.getPayMethod());
        p.setPayDate(req.getPayDate());
        p.setStatus(ST_DRAFT);
        p.setDeptId(req.getDeptId());
        p.setRemark(req.getRemark());
        p.setIsDeleted(0);
        p.setCreatedBy(userId);
        p.setCreatedAt(LocalDateTime.now());
        paymentMapper.insert(p);
        if (budgetId != null) {
            budgetService.freeze(budgetId, amount, "PAYMENT", p.getId(), userId, "付款申请冻结 " + p.getPaymentNo());
        }
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrder submit(Long id, Long userId) {
        PaymentOrder p = require(id);
        if (!ST_DRAFT.equals(p.getStatus()) && !ST_REJECTED.equals(p.getStatus())) {
            throw new BizException("当前状态不可提交");
        }
        p.setStatus(ST_PENDING);
        p.setUpdatedBy(userId);
        p.setUpdatedAt(LocalDateTime.now());
        paymentMapper.updateById(p);
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrder confirmPaid(Long id, Long userId) {
        PaymentOrder p = require(id);
        if (!ST_PENDING.equals(p.getStatus())) {
            throw new BizException("当前状态不可确认付款");
        }
        if (p.getBudgetId() != null) {
            budgetService.consumeFromFrozen(p.getBudgetId(), p.getAmount(), "PAYMENT", p.getId(), userId, "付款完成消耗");
        }
        if (p.getContractId() != null) {
            contractService.addPaidAmount(p.getContractId(), p.getAmount(), userId);
            if (p.getPayNodeId() != null) {
                ContractPaymentNode node = nodeMapper.selectById(p.getPayNodeId());
                if (node != null) {
                    BigDecimal paid = (node.getPaidAmount() == null ? BigDecimal.ZERO : node.getPaidAmount())
                            .add(p.getAmount()).setScale(2, RoundingMode.HALF_UP);
                    node.setPaidAmount(paid);
                    node.setStatus(paid.compareTo(node.getPlanAmount() == null ? BigDecimal.ZERO : node.getPlanAmount()) >= 0
                            ? "PAID" : "PARTIAL");
                    nodeMapper.updateById(node);
                }
            }
        }
        p.setStatus(ST_PAID);
        p.setUpdatedBy(userId);
        p.setUpdatedAt(LocalDateTime.now());
        paymentMapper.updateById(p);
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrder reject(Long id, String reason, Long userId) {
        PaymentOrder p = require(id);
        if (!ST_PENDING.equals(p.getStatus()) && !ST_DRAFT.equals(p.getStatus())) {
            throw new BizException("当前状态不可驳回");
        }
        releaseBudgetIfAny(p, userId, "付款驳回释放");
        p.setStatus(ST_REJECTED);
        p.setRemark(reason == null ? p.getRemark() : reason);
        p.setUpdatedBy(userId);
        p.setUpdatedAt(LocalDateTime.now());
        paymentMapper.updateById(p);
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentOrder cancel(Long id, Long userId) {
        PaymentOrder p = require(id);
        if (ST_PAID.equals(p.getStatus())) {
            throw new BizException("已付款不可作废，请走冲销");
        }
        if (ST_CANCELLED.equals(p.getStatus())) {
            return p;
        }
        releaseBudgetIfAny(p, userId, "付款作废释放");
        p.setStatus(ST_CANCELLED);
        p.setUpdatedBy(userId);
        p.setUpdatedAt(LocalDateTime.now());
        paymentMapper.updateById(p);
        return p;
    }

    private void releaseBudgetIfAny(PaymentOrder p, Long userId, String remark) {
        if (p.getBudgetId() != null && (p.getAmount() != null && p.getAmount().compareTo(BigDecimal.ZERO) > 0)
                && !ST_PAID.equals(p.getStatus())) {
            budgetService.release(p.getBudgetId(), p.getAmount(), "PAYMENT", p.getId(), userId, remark);
        }
    }

    private BigDecimal sumInTransit(Long contractId) {
        List<PaymentOrder> list = paymentMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getContractId, contractId)
                .in(PaymentOrder::getStatus, ST_DRAFT, ST_PENDING));
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentOrder o : list) {
            if (o.getAmount() != null) sum = sum.add(o.getAmount());
        }
        return sum;
    }

    /** 应付账龄：按供应商汇总未付合同余额 + 已开票未结算粗算 */
    public List<Map<String, Object>> apAging() {
        // 简化：扫描 ACTIVE 合同 unpaid，按 end_date/sign_date 与今天差值分桶
        List<PurchaseContract> contracts = contractService /* reuse mapper via page */
                .page(1, 500, "ACTIVE", null, null).getRecords();
        Map<Long, Map<String, Object>> bySupplier = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (PurchaseContract c : contracts) {
            BigDecimal unpaid = (c.getContractAmount() == null ? BigDecimal.ZERO : c.getContractAmount())
                    .subtract(c.getPaidAmount() == null ? BigDecimal.ZERO : c.getPaidAmount());
            if (unpaid.compareTo(BigDecimal.ZERO) <= 0) continue;
            LocalDate base = c.getSignDate() != null ? c.getSignDate() : c.getCreatedAt() == null ? today : c.getCreatedAt().toLocalDate();
            long days = java.time.temporal.ChronoUnit.DAYS.between(base, today);
            String bucket = days <= 30 ? "0-30" : days <= 60 ? "31-60" : days <= 90 ? "61-90" : "90+";
            Long sid = c.getSupplierId() == null ? 0L : c.getSupplierId();
            Map<String, Object> row = bySupplier.computeIfAbsent(sid, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("supplierId", k);
                m.put("totalUnpaid", BigDecimal.ZERO);
                m.put("b0_30", BigDecimal.ZERO);
                m.put("b31_60", BigDecimal.ZERO);
                m.put("b61_90", BigDecimal.ZERO);
                m.put("b90p", BigDecimal.ZERO);
                return m;
            });
            String key = switch (bucket) {
                case "0-30" -> "b0_30";
                case "31-60" -> "b31_60";
                case "61-90" -> "b61_90";
                default -> "b90p";
            };
            row.put("totalUnpaid", ((BigDecimal) row.get("totalUnpaid")).add(unpaid));
            row.put(key, ((BigDecimal) row.get(key)).add(unpaid));
        }
        return new ArrayList<>(bySupplier.values());
    }

    public PaymentOrder require(Long id) {
        PaymentOrder p = paymentMapper.selectById(id);
        if (p == null || (p.getIsDeleted() != null && p.getIsDeleted() == 1)) {
            throw new BizException("付款单不存在");
        }
        return p;
    }
}
