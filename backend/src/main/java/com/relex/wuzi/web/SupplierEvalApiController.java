package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.SupplierEvalScore;
import com.relex.wuzi.service.SupplierEvalService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商评价模型 API：GET /api/suppliers/{id}/eval 计算并落库 supplier_eval_score。
 * 与既有 SupplierEvalController（/{id}/summary）互补。
 */
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierEvalApiController {

    private final SupplierEvalService supplierEvalService;

    @Data
    public static class RecalcReq {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private BigDecimal weightOnTime;
        private BigDecimal weightReturn;
        private BigDecimal weightPrice;
    }

    /** 计算评分（默认权重 40/30/30）并写入 supplier_eval_score */
    @GetMapping("/{id}/eval")
    public R<Map<String, Object>> eval(@PathVariable Long id,
                                       @RequestParam(required = false) LocalDate periodStart,
                                       @RequestParam(required = false) LocalDate periodEnd,
                                       @RequestParam(required = false) BigDecimal weightOnTime,
                                       @RequestParam(required = false) BigDecimal weightReturn,
                                       @RequestParam(required = false) BigDecimal weightPrice,
                                       @RequestAttribute("userId") Long userId) {
        SupplierEvalService.EvalResult result = supplierEvalService.evaluate(
                id, periodStart, periodEnd, weightOnTime, weightReturn, weightPrice, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("result", result);
        data.put("history", supplierEvalService.history(id, 10));
        return R.ok(data);
    }

    /** 自定义权重重算 */
    @RequirePerm("base:supplier:eval-recalc")
    @PostMapping("/{id}/eval/recalc")
    public R<Map<String, Object>> recalc(@PathVariable Long id,
                                         @RequestBody(required = false) RecalcReq req,
                                         @RequestAttribute("userId") Long userId) {
        RecalcReq body = req == null ? new RecalcReq() : req;
        SupplierEvalService.EvalResult result = supplierEvalService.evaluate(
                id, body.getPeriodStart(), body.getPeriodEnd(),
                body.getWeightOnTime(), body.getWeightReturn(), body.getWeightPrice(), userId);
        Map<String, Object> data = new HashMap<>();
        data.put("result", result);
        return R.ok(data);
    }

    /** 历史评分 */
    @GetMapping("/{id}/eval/history")
    public R<List<SupplierEvalScore>> history(@PathVariable Long id,
                                              @RequestParam(defaultValue = "20") int limit) {
        return R.ok(supplierEvalService.history(id, limit));
    }
}
