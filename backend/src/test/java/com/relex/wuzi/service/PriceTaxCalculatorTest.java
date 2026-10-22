package com.relex.wuzi.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 税价与可入库量契约（纯单元，不启 Spring）。
 */
class PriceTaxCalculatorTest {

    private final PriceTaxCalculator calc = new PriceTaxCalculator();

    @Test
    void fromInclusiveSplitsTax() {
        PriceTaxCalculator.Line line = calc.fromInclusive(
                new BigDecimal("113"), new BigDecimal("13"), new BigDecimal("2"));
        assertNotNull(line);
        assertEquals(0, new BigDecimal("226").compareTo(line.taxInclusiveAmount));
        // 不含税 = 含税 / 1.13
        BigDecimal expectedExcl = new BigDecimal("226").divide(new BigDecimal("1.13"), 2, java.math.RoundingMode.HALF_UP);
        assertEquals(0, expectedExcl.compareTo(line.taxExclAmount));
    }

    @Test
    void zeroRateNoTax() {
        PriceTaxCalculator.Line line = calc.fromInclusive(
                new BigDecimal("100"), new BigDecimal("0"), new BigDecimal("1"));
        assertEquals(0, new BigDecimal("100").compareTo(line.taxInclusiveAmount));
        assertEquals(0, new BigDecimal("100").compareTo(line.taxExclAmount));
        assertEquals(0, BigDecimal.ZERO.compareTo(line.taxAmount));
    }
}
