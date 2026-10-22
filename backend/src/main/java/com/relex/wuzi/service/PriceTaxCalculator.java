package com.relex.wuzi.service;

import com.relex.wuzi.common.BizException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 全局唯一税价计算器。主路径：T = Ai - An，保证含税=不含税+税额。
 */
@Service
public class PriceTaxCalculator {

    public static final int PRICE_SCALE = 6;
    public static final int AMOUNT_SCALE = 2;

    public static class Line {
        public BigDecimal taxInclusivePrice;
        public BigDecimal taxExclusivePrice;
        public BigDecimal taxUnitAmount;
        public BigDecimal qty;
        public BigDecimal taxRate;
        public BigDecimal taxInclusiveAmount;
        public BigDecimal taxExclAmount;
        public BigDecimal taxAmount;
    }

    /** 由含税单价反算（采购主路径） */
    public Line fromInclusive(BigDecimal inclPrice, BigDecimal taxRate, BigDecimal qty) {
        validate(inclPrice, taxRate, qty);
        Line l = new Line();
        l.taxRate = taxRate;
        l.qty = qty;
        l.taxInclusivePrice = inclPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        BigDecimal rate = taxRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        l.taxExclusivePrice = inclPrice.divide(BigDecimal.ONE.add(rate), PRICE_SCALE, RoundingMode.HALF_UP);
        l.taxUnitAmount = l.taxInclusivePrice.subtract(l.taxExclusivePrice).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        l.taxInclusiveAmount = inclPrice.multiply(qty).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        l.taxExclAmount = l.taxExclusivePrice.multiply(qty).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        l.taxAmount = l.taxInclusiveAmount.subtract(l.taxExclAmount);
        return l;
    }

    /** 由不含税单价正算 */
    public Line fromExclusive(BigDecimal exclPrice, BigDecimal taxRate, BigDecimal qty) {
        validate(exclPrice, taxRate, qty);
        BigDecimal rate = taxRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal incl = exclPrice.multiply(BigDecimal.ONE.add(rate)).setScale(PRICE_SCALE, RoundingMode.HALF_UP);
        return fromInclusive(incl, taxRate, qty);
    }

    public void validatePrices(BigDecimal incl, BigDecimal excl, BigDecimal taxRate) {
        BigDecimal rate = taxRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal expectExcl = incl.divide(BigDecimal.ONE.add(rate), PRICE_SCALE, RoundingMode.HALF_UP);
        if (expectExcl.subtract(excl).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BizException("含税价与不含税价换算不一致");
        }
    }

    private void validate(BigDecimal price, BigDecimal taxRate, BigDecimal qty) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("含税单价必须大于0");
        }
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("税率非法");
        }
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("数量必须大于0");
        }
    }
}
