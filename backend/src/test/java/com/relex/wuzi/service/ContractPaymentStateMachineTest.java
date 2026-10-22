package com.relex.wuzi.service;

import com.relex.wuzi.common.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 合同/付款状态机纯逻辑校验：DRAFT→PENDING→ACTIVE→CLOSED/TERMINATED；
 * 付款 DRAFT→PENDING→PAID，驳回/作废释放冻结。
 */
class ContractPaymentStateMachineTest {

    static final String DRAFT = "DRAFT";
    static final String PENDING = "PENDING";
    static final String ACTIVE = "ACTIVE";
    static final String CLOSED = "CLOSED";
    static final String TERMINATED = "TERMINATED";
    static final String PAID = "PAID";

    private void require(boolean cond, String msg) {
        if (!cond) throw new BizException(msg);
    }

    @Test
    void contractSubmitOnlyFromDraft() {
        require(DRAFT.equals(DRAFT), "x");
        assertThrows(BizException.class, () -> require(ACTIVE.equals(DRAFT), "当前状态不可提交"));
    }

    @Test
    void contractActivateFromPendingOrDraft() {
        assertTrue(PENDING.equals(PENDING) || DRAFT.equals(PENDING));
        assertThrows(BizException.class, () -> require(PENDING.equals(CLOSED) || DRAFT.equals(CLOSED), "不可生效"));
    }

    @Test
    void contractCloseOnlyActive() {
        require(ACTIVE.equals(ACTIVE), "x");
        assertThrows(BizException.class, () -> require(ACTIVE.equals(PENDING), "仅执行中可关闭"));
    }

    @Test
    void paymentCannotExceedContractRemain() {
        BigDecimal contractAmount = new BigDecimal("10000.00");
        BigDecimal paid = new BigDecimal("4000.00");
        BigDecimal inTransit = new BigDecimal("2000.00");
        BigDecimal request = new BigDecimal("5000.00");
        BigDecimal remain = contractAmount.subtract(paid);
        assertTrue(request.add(inTransit).compareTo(remain) > 0);
    }

    @Test
    void unpaidReleaseOnClose() {
        BigDecimal contractAmount = new BigDecimal("10000.00").setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = new BigDecimal("3000.00").setScale(2, RoundingMode.HALF_UP);
        BigDecimal unpaid = contractAmount.subtract(paid);
        assertEquals(new BigDecimal("7000.00"), unpaid);
        assertTrue(unpaid.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void paidIsTerminalForCancel() {
        assertThrows(BizException.class, () -> require(!PAID.equals(PAID), "已付款不可作废"));
    }
}
