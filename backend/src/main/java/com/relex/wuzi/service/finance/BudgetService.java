package com.relex.wuzi.service.finance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.Budget;
import com.relex.wuzi.domain.BudgetControlLog;
import com.relex.wuzi.mapper.BudgetControlLogMapper;
import com.relex.wuzi.mapper.BudgetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预算与费用控制：冻结 / 消耗 / 释放，超预算拦截。
 * 可用余额 = budgetAmount - usedAmount - frozenAmount。
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    public static final String ACTION_FREEZE = "FREEZE";
    public static final String ACTION_CONSUME = "CONSUME";
    public static final String ACTION_RELEASE = "RELEASE";
    public static final String ACTION_UNCONSUME = "UNCONSUME";

    private final BudgetMapper budgetMapper;
    private final BudgetControlLogMapper logMapper;

    public BigDecimal available(Budget b) {
        if (b == null) return BigDecimal.ZERO;
        return nvl(b.getBudgetAmount()).subtract(nvl(b.getUsedAmount())).subtract(nvl(b.getFrozenAmount()));
    }

    /** 按范围查找可用预算（首个命中） */
    public Budget findBudget(Integer year, Long deptId, String projectCode, String category) {
        String cat = category == null || category.isBlank() ? "PURCHASE" : category;
        LambdaQueryWrapper<Budget> qw = new LambdaQueryWrapper<Budget>()
                .eq(Budget::getBudgetYear, year)
                .eq(Budget::getCategory, cat)
                .eq(Budget::getStatus, "ACTIVE")
                .eq(Budget::getIsDeleted, 0)
                .orderByDesc(Budget::getId);
        if (deptId != null) {
            qw.eq(Budget::getDeptId, deptId);
        } else {
            qw.isNull(Budget::getDeptId);
        }
        if (projectCode == null || projectCode.isBlank()) {
            qw.and(w -> w.isNull(Budget::getProjectCode).or().eq(Budget::getProjectCode, ""));
        } else {
            qw.eq(Budget::getProjectCode, projectCode);
        }
        return budgetMapper.selectOne(qw);
    }

    /** 校验并冻结金额（合同签订、付款申请等） */
    @Transactional(rollbackFor = Exception.class)
    public Budget freeze(Long budgetId, BigDecimal amount, String bizType, Long bizId, Long userId, String remark) {
        return mutate(budgetId, amount, ACTION_FREEZE, bizType, bizId, userId, remark);
    }

    /** 冻结转消耗（付款完成） */
    @Transactional(rollbackFor = Exception.class)
    public Budget consumeFromFrozen(Long budgetId, BigDecimal amount, String bizType, Long bizId, Long userId, String remark) {
        return mutate(budgetId, amount, ACTION_CONSUME, bizType, bizId, userId, remark);
    }

    /** 直接消耗（不走冻结的场景） */
    @Transactional(rollbackFor = Exception.class)
    public Budget consume(Long budgetId, BigDecimal amount, String bizType, Long bizId, Long userId, String remark) {
        return mutate(budgetId, amount, ACTION_CONSUME, bizType, bizId, userId, remark);
    }

    /** 释放冻结（取消合同/驳回付款） */
    @Transactional(rollbackFor = Exception.class)
    public Budget release(Long budgetId, BigDecimal amount, String bizType, Long bizId, Long userId, String remark) {
        return mutate(budgetId, amount, ACTION_RELEASE, bizType, bizId, userId, remark);
    }

    /** 回滚消耗 */
    @Transactional(rollbackFor = Exception.class)
    public Budget unconsume(Long budgetId, BigDecimal amount, String bizType, Long bizId, Long userId, String remark) {
        return mutate(budgetId, amount, ACTION_UNCONSUME, bizType, bizId, userId, remark);
    }

    private Budget mutate(Long budgetId, BigDecimal amount, String action, String bizType, Long bizId,
                          Long userId, String remark) {
        if (budgetId == null) {
            throw new BizException("budgetId 不能为空");
        }
        BigDecimal amt = nvl(amount);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("金额必须大于 0");
        }
        Budget b = budgetMapper.selectById(budgetId);
        if (b == null || (b.getIsDeleted() != null && b.getIsDeleted() == 1)) {
            throw new BizException("预算不存在");
        }
        if (!"ACTIVE".equals(b.getStatus())) {
            throw new BizException("预算不可用：" + b.getStatus());
        }
        BigDecimal beforeUsed = nvl(b.getUsedAmount());
        BigDecimal beforeFrozen = nvl(b.getFrozenAmount());
        BigDecimal afterUsed = beforeUsed;
        BigDecimal afterFrozen = beforeFrozen;
        switch (action) {
            case ACTION_FREEZE -> {
                afterFrozen = beforeFrozen.add(amt);
                if (nvl(b.getBudgetAmount()).subtract(afterUsed).subtract(afterFrozen).compareTo(BigDecimal.ZERO) < 0) {
                    throw new BizException("预算不足：可用 "
                            + available(b).setScale(2, RoundingMode.HALF_UP)
                            + "，申请 " + amt.setScale(2, RoundingMode.HALF_UP));
                }
            }
            case ACTION_CONSUME -> {
                if (beforeFrozen.compareTo(amt) >= 0) {
                    afterFrozen = beforeFrozen.subtract(amt);
                    afterUsed = beforeUsed.add(amt);
                } else if (beforeFrozen.compareTo(BigDecimal.ZERO) > 0) {
                    // 部分冻结转消耗，剩余直接消耗
                    BigDecimal fromFrozen = beforeFrozen;
                    BigDecimal direct = amt.subtract(fromFrozen);
                    afterFrozen = BigDecimal.ZERO;
                    afterUsed = beforeUsed.add(amt);
                    if (nvl(b.getBudgetAmount()).subtract(afterUsed).compareTo(BigDecimal.ZERO) < 0) {
                        throw new BizException("预算不足");
                    }
                } else {
                    afterUsed = beforeUsed.add(amt);
                    if (nvl(b.getBudgetAmount()).subtract(afterUsed).compareTo(BigDecimal.ZERO) < 0) {
                        throw new BizException("预算不足");
                    }
                }
            }
            case ACTION_RELEASE -> {
                if (beforeFrozen.compareTo(amt) < 0) {
                    throw new BizException("释放金额超过冻结额");
                }
                afterFrozen = beforeFrozen.subtract(amt);
            }
            case ACTION_UNCONSUME -> {
                if (beforeUsed.compareTo(amt) < 0) {
                    throw new BizException("回滚金额超过已消耗");
                }
                afterUsed = beforeUsed.subtract(amt);
            }
            default -> throw new BizException("未知预算动作：" + action);
        }
        b.setUsedAmount(afterUsed.setScale(2, RoundingMode.HALF_UP));
        b.setFrozenAmount(afterFrozen.setScale(2, RoundingMode.HALF_UP));
        budgetMapper.update(null, new LambdaUpdateWrapper<Budget>()
                .eq(Budget::getId, b.getId())
                .set(Budget::getUsedAmount, b.getUsedAmount())
                .set(Budget::getFrozenAmount, b.getFrozenAmount())
                .set(Budget::getUpdatedAt, LocalDateTime.now()));

        BudgetControlLog log = new BudgetControlLog();
        log.setBudgetId(b.getId());
        log.setAction(action);
        log.setAmount(amt.setScale(2, RoundingMode.HALF_UP));
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setBeforeUsed(beforeUsed.setScale(2, RoundingMode.HALF_UP));
        log.setBeforeFrozen(beforeFrozen.setScale(2, RoundingMode.HALF_UP));
        log.setAfterUsed(b.getUsedAmount());
        log.setAfterFrozen(b.getFrozenAmount());
        log.setRemark(remark);
        log.setCreatedBy(userId);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
        return b;
    }

    public List<BudgetControlLog> logs(Long budgetId) {
        return logMapper.selectList(new LambdaQueryWrapper<BudgetControlLog>()
                .eq(BudgetControlLog::getBudgetId, budgetId)
                .orderByDesc(BudgetControlLog::getId));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
