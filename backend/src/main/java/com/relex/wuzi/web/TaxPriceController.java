package com.relex.wuzi.web;

import com.relex.wuzi.annotation.LoginOnly;
import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.TaxRateDict;
import com.relex.wuzi.mapper.TaxRateDictMapper;
import com.relex.wuzi.service.PriceTaxCalculator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaxPriceController {

    private final TaxRateDictMapper taxRateDictMapper;
    private final PriceTaxCalculator calculator;

    @GetMapping("/tax-rates")
    public R<List<TaxRateDict>> listTaxRates(@RequestParam(required = false) Boolean onlyEnabled) {
        LambdaQueryWrapper<TaxRateDict> qw = new LambdaQueryWrapper<TaxRateDict>()
                .eq(TaxRateDict::getIsDeleted, 0)
                .eq(Boolean.TRUE.equals(onlyEnabled), TaxRateDict::getIsEnabled, 1)
                .orderByAsc(TaxRateDict::getTaxRate)
                .orderByAsc(TaxRateDict::getId);
        return R.ok(taxRateDictMapper.selectList(qw));
    }

    @Data
    public static class SaveTaxRateReq {
        private Long id;
        private String taxCode;
        private String taxName;
        private BigDecimal taxRate;
        private String taxCategory;
        private Integer isDefault;
        private Integer isEnabled;
        private String remark;
    }

    @RequirePerm("base:tax-rate:create")
    @PostMapping("/tax-rates")
    public R<TaxRateDict> saveTaxRate(@RequestBody SaveTaxRateReq req) {
        if (req.getTaxCode() == null || req.getTaxCode().isBlank()) {
            throw new BizException("税率编码不能为空");
        }
        if (req.getTaxName() == null || req.getTaxName().isBlank()) {
            throw new BizException("税率名称不能为空");
        }
        if (req.getTaxRate() == null || req.getTaxRate().signum() < 0) {
            throw new BizException("税率必须大于等于 0");
        }
        Long exists = taxRateDictMapper.selectCount(new LambdaQueryWrapper<TaxRateDict>()
                .eq(TaxRateDict::getTaxCode, req.getTaxCode().trim())
                .eq(TaxRateDict::getIsDeleted, 0)
                .ne(req.getId() != null, TaxRateDict::getId, req.getId()));
        if (exists != null && exists > 0) {
            throw new BizException("税率编码已存在：" + req.getTaxCode());
        }

        TaxRateDict row = req.getId() == null ? new TaxRateDict() : taxRateDictMapper.selectById(req.getId());
        if (row == null) {
            throw new BizException("税率不存在");
        }
        row.setTaxCode(req.getTaxCode().trim());
        row.setTaxName(req.getTaxName().trim());
        row.setTaxRate(req.getTaxRate());
        row.setTaxCategory(req.getTaxCategory());
        row.setIsDefault(req.getIsDefault() == null ? 0 : req.getIsDefault());
        row.setIsEnabled(req.getIsEnabled() == null ? 1 : req.getIsEnabled());
        row.setRemark(req.getRemark());
        if (row.getIsDeleted() == null) {
            row.setIsDeleted(0);
        }

        if (row.getIsDefault() == 1) {
            taxRateDictMapper.update(null, new LambdaUpdateWrapper<TaxRateDict>()
                    .eq(TaxRateDict::getIsDeleted, 0)
                    .ne(req.getId() != null, TaxRateDict::getId, req.getId())
                    .set(TaxRateDict::getIsDefault, 0));
        }

        if (row.getId() == null) {
            row.setCreatedAt(LocalDateTime.now());
            taxRateDictMapper.insert(row);
        } else {
            row.setUpdatedAt(LocalDateTime.now());
            taxRateDictMapper.updateById(row);
        }
        return R.ok(row);
    }

    @RequirePerm("base:tax-rate:toggle")
    @PostMapping("/tax-rates/{id}/toggle")
    public R<TaxRateDict> toggleTaxRate(@PathVariable Long id) {
        TaxRateDict row = requireTaxRate(id);
        row.setIsEnabled(row.getIsEnabled() != null && row.getIsEnabled() == 1 ? 0 : 1);
        row.setUpdatedAt(LocalDateTime.now());
        taxRateDictMapper.updateById(row);
        return R.ok(row);
    }

    @RequirePerm("base:tax-rate:delete")
    @DeleteMapping("/tax-rates/{id}")
    public R<Void> deleteTaxRate(@PathVariable Long id) {
        TaxRateDict row = requireTaxRate(id);
        if (row.getIsDefault() != null && row.getIsDefault() == 1) {
            throw new BizException("默认税率不可删除，请先取消默认");
        }
        row.setIsDeleted(1);
        row.setUpdatedAt(LocalDateTime.now());
        taxRateDictMapper.updateById(row);
        return R.ok(null);
    }

    private TaxRateDict requireTaxRate(Long id) {
        TaxRateDict row = taxRateDictMapper.selectById(id);
        if (row == null || (row.getIsDeleted() != null && row.getIsDeleted() == 1)) {
            throw new BizException("税率不存在");
        }
        return row;
    }

    @Data
    public static class CalcReq {
        private BigDecimal taxInclusivePrice;
        private BigDecimal taxExclusivePrice;
        private BigDecimal taxRate;
        private BigDecimal qty = BigDecimal.ONE;
        private String mode = "INCLUSIVE";
    }

    @LoginOnly("税价试算")
    @PostMapping("/price/calc")
    public R<PriceTaxCalculator.Line> calc(@RequestBody CalcReq req) {
        PriceTaxCalculator.Line line;
        if ("EXCLUSIVE".equalsIgnoreCase(req.getMode())) {
            line = calculator.fromExclusive(req.getTaxExclusivePrice(), req.getTaxRate(), req.getQty());
        } else {
            line = calculator.fromInclusive(req.getTaxInclusivePrice(), req.getTaxRate(), req.getQty());
        }
        return R.ok(line);
    }

    @GetMapping("/price/example")
    public R<Map<String, Object>> example() {
        // 与设计说明 12A.10 对齐的验算示例
        var l1 = calculator.fromInclusive(new BigDecimal("22"), new BigDecimal("13"), new BigDecimal("100"));
        var l2 = calculator.fromInclusive(new BigDecimal("18"), new BigDecimal("13"), new BigDecimal("50"));
        var l3 = calculator.fromInclusive(new BigDecimal("500"), new BigDecimal("6"), new BigDecimal("1"));
        BigDecimal sumIncl = l1.taxInclusiveAmount.add(l2.taxInclusiveAmount).add(l3.taxInclusiveAmount);
        BigDecimal sumExcl = l1.taxExclAmount.add(l2.taxExclAmount).add(l3.taxExclAmount);
        BigDecimal sumTax = l1.taxAmount.add(l2.taxAmount).add(l3.taxAmount);
        return R.ok(Map.of(
                "lines", List.of(l1, l2, l3),
                "sumTaxInclusive", sumIncl,
                "sumTaxExclusive", sumExcl,
                "sumTaxAmount", sumTax
        ));
    }
}
