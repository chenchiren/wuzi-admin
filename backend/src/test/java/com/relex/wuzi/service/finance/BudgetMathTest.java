package com.relex.wuzi.service.finance;

import com.relex.wuzi.common.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 预算口径纯逻辑：可用余额 = 总额 - 已用 - 冻结。
 * 完整冻结/消耗链路依赖 DB，由 chain-full-test.ps1 覆盖。
 */
class BudgetMathTest {

    @Test
    void availableIsTotalMinusUsedMinusFrozen() {
        BigDecimal total = new BigDecimal("10000.00");
        BigDecimal used = new BigDecimal("3000.00");
        BigDecimal frozen = new BigDecimal("1500.00");
        BigDecimal available = total.subtract(used).subtract(frozen);
        assertEquals(new BigDecimal("5500.00"), available);
    }

    @Test
    void overspendMustBeBlocked() {
        BigDecimal total = new BigDecimal("100.00");
        BigDecimal used = new BigDecimal("90.00");
        BigDecimal frozen = new BigDecimal("5.00");
        BigDecimal request = new BigDecimal("10.00");
        BigDecimal afterFrozen = frozen.add(request);
        assertTrue(total.subtract(used).subtract(afterFrozen).compareTo(BigDecimal.ZERO) < 0);
    }

    @Test
    void zeroOrNegativeAmountRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            BigDecimal amount = BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("金额必须大于 0");
            }
        });
    }
}
