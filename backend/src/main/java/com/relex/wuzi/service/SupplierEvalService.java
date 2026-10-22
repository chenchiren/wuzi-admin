package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.PurchaseArrival;
import com.relex.wuzi.domain.PurchaseOrder;
import com.relex.wuzi.domain.PurchaseOrderItem;
import com.relex.wuzi.domain.PurchaseReturn;
import com.relex.wuzi.domain.Supplier;
import com.relex.wuzi.domain.SupplierEvalScore;
import com.relex.wuzi.domain.SupplierProduct;
import com.relex.wuzi.domain.SupplierProductPrice;
import com.relex.wuzi.mapper.PurchaseArrivalMapper;
import com.relex.wuzi.mapper.PurchaseOrderItemMapper;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.PurchaseReturnMapper;
import com.relex.wuzi.mapper.SupplierEvalScoreMapper;
import com.relex.wuzi.mapper.SupplierMapper;
import com.relex.wuzi.mapper.SupplierProductMapper;
import com.relex.wuzi.mapper.SupplierProductPriceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 供应商评价模型：交货准时率 / 退货率 / 价格分，权重可配，结果写 supplier_eval_score。
 * 总分 = w1×准时率 + w2×退货得分 + w3×价格分（权重归一化，百分制）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierEvalService {

    public static final BigDecimal DEFAULT_W_ON_TIME = new BigDecimal("40");
    public static final BigDecimal DEFAULT_W_RETURN = new BigDecimal("30");
    public static final BigDecimal DEFAULT_W_PRICE = new BigDecimal("30");

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SupplierMapper supplierMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final PurchaseArrivalMapper purchaseArrivalMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final SupplierProductMapper supplierProductMapper;
    private final SupplierProductPriceMapper supplierProductPriceMapper;
    private final SupplierEvalScoreMapper evalScoreMapper;

    public static class EvalResult {
        public Long supplierId;
        public String supplierName;
        public LocalDate periodStart;
        public LocalDate periodEnd;
        public BigDecimal onTimeRate;
        public BigDecimal returnRate;
        public BigDecimal priceScore;
        public BigDecimal weightOnTime;
        public BigDecimal weightReturn;
        public BigDecimal weightPrice;
        public BigDecimal totalScore;
        public int orderCount;
        public int returnCount;
        public Map<String, Object> detail;
        public Long scoreId;
    }

    /** 计算并落库 */
    @Transactional(rollbackFor = Exception.class)
    /** 批量重算全部供应商（定时任务用） */
    public int recalcAllSuppliers() {
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getIsDeleted, 0));
        int n = 0;
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(90);
        for (Supplier s : suppliers) {
            try {
                evaluate(s.getId(), start, end, DEFAULT_W_ON_TIME, DEFAULT_W_RETURN, DEFAULT_W_PRICE, 0L);
                n++;
            } catch (Exception e) {
                log.warn("eval skip supplier {}: {}", s.getId(), e.getMessage());
            }
        }
        return n;
    }

    public EvalResult evaluate(Long supplierId,
                               LocalDate periodStart,
                               LocalDate periodEnd,
                               BigDecimal weightOnTime,
                               BigDecimal weightReturn,
                               BigDecimal weightPrice,
                               Long operatorId) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null || (supplier.getIsDeleted() != null && supplier.getIsDeleted() == 1)) {
            throw new BizException("供应商不存在");
        }
        LocalDate end = periodEnd != null ? periodEnd : LocalDate.now();
        LocalDate start = periodStart != null ? periodStart : end.minusMonths(12);

        List<PurchaseOrder> orders = purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getSupplierId, supplierId)
                .eq(PurchaseOrder::getIsDeleted, 0)
                .ge(PurchaseOrder::getOrderDate, start)
                .le(PurchaseOrder::getOrderDate, end));

        List<PurchaseReturn> returns = purchaseReturnMapper.selectList(new LambdaQueryWrapper<PurchaseReturn>()
                .eq(PurchaseReturn::getSupplierId, supplierId)
                .eq(PurchaseReturn::getIsDeleted, 0));

        OnTimeStat onTime = calcOnTime(orders);
        ReturnStat returnStat = calcReturn(orders, returns);
        PriceStat priceStat = calcPrice(supplierId);

        BigDecimal w1 = nz(weightOnTime, DEFAULT_W_ON_TIME);
        BigDecimal w2 = nz(weightReturn, DEFAULT_W_RETURN);
        BigDecimal w3 = nz(weightPrice, DEFAULT_W_PRICE);
        BigDecimal wSum = w1.add(w2).add(w3);
        if (wSum.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("权重之和必须大于0");
        }

        BigDecimal returnScore = BigDecimal.valueOf(100).subtract(returnStat.returnRate)
                .max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
        BigDecimal total = onTime.onTimeRate.multiply(w1)
                .add(returnScore.multiply(w2))
                .add(priceStat.priceScore.multiply(w3))
                .divide(wSum, 2, RoundingMode.HALF_UP);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("onTime", onTime.detail);
        detail.put("returnStat", returnStat.detail);
        detail.put("price", priceStat.detail);
        detail.put("returnScore", returnScore.setScale(2, RoundingMode.HALF_UP));

        SupplierEvalScore row = new SupplierEvalScore();
        row.setSupplierId(supplierId);
        row.setEvalDate(LocalDate.now());
        row.setPeriodStart(start);
        row.setPeriodEnd(end);
        row.setOnTimeRate(onTime.onTimeRate.setScale(2, RoundingMode.HALF_UP));
        row.setReturnRate(returnStat.returnRate.setScale(2, RoundingMode.HALF_UP));
        row.setPriceScore(priceStat.priceScore.setScale(2, RoundingMode.HALF_UP));
        row.setWeightOnTime(w1.setScale(2, RoundingMode.HALF_UP));
        row.setWeightReturn(w2.setScale(2, RoundingMode.HALF_UP));
        row.setWeightPrice(w3.setScale(2, RoundingMode.HALF_UP));
        row.setTotalScore(total.setScale(2, RoundingMode.HALF_UP));
        row.setOrderCount(orders.size());
        row.setReturnCount(returns.size());
        try {
            row.setDetailJson(MAPPER.writeValueAsString(detail));
        } catch (Exception e) {
            row.setDetailJson("{}");
            log.warn("eval detail serialize fail", e);
        }
        row.setCreatedBy(operatorId);
        evalScoreMapper.insert(row);

        EvalResult r = new EvalResult();
        r.supplierId = supplierId;
        r.supplierName = supplier.getSupplierName();
        r.periodStart = start;
        r.periodEnd = end;
        r.onTimeRate = row.getOnTimeRate();
        r.returnRate = row.getReturnRate();
        r.priceScore = row.getPriceScore();
        r.weightOnTime = row.getWeightOnTime();
        r.weightReturn = row.getWeightReturn();
        r.weightPrice = row.getWeightPrice();
        r.totalScore = row.getTotalScore();
        r.orderCount = orders.size();
        r.returnCount = returns.size();
        r.detail = detail;
        r.scoreId = row.getId();
        return r;
    }

    /** 历史评分列表 */
    public List<SupplierEvalScore> history(Long supplierId, int limit) {
        return evalScoreMapper.selectList(new LambdaQueryWrapper<SupplierEvalScore>()
                .eq(SupplierEvalScore::getSupplierId, supplierId)
                .orderByDesc(SupplierEvalScore::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    private static class OnTimeStat {
        BigDecimal onTimeRate = BigDecimal.valueOf(100);
        Map<String, Object> detail = new HashMap<>();
    }

    private static class ReturnStat {
        BigDecimal returnRate = BigDecimal.ZERO;
        Map<String, Object> detail = new HashMap<>();
    }

    private static class PriceStat {
        BigDecimal priceScore = BigDecimal.valueOf(100);
        Map<String, Object> detail = new HashMap<>();
    }

    /** 交货准时率：到货日期 <= 期望到货日期 视为准时 */
    private OnTimeStat calcOnTime(List<PurchaseOrder> orders) {
        OnTimeStat stat = new OnTimeStat();
        int total = 0;
        int onTime = 0;
        for (PurchaseOrder order : orders) {
            if (!"RECEIVED".equals(order.getStatus()) && !"COMPLETED".equals(order.getStatus())) {
                continue;
            }
            total++;
            PurchaseArrival arrival = purchaseArrivalMapper.selectOne(new LambdaQueryWrapper<PurchaseArrival>()
                    .eq(PurchaseArrival::getOrderId, order.getId())
                    .eq(PurchaseArrival::getIsDeleted, 0)
                    .orderByAsc(PurchaseArrival::getId)
                    .last("LIMIT 1"));
            LocalDate expect = order.getExpectArrivalDate();
            LocalDate actual = arrival != null && arrival.getArrivalTime() != null
                    ? arrival.getArrivalTime().toLocalDate() : order.getOrderDate();
            if (expect == null || (actual != null && !actual.isAfter(expect))) {
                onTime++;
            }
        }
        if (total > 0) {
            stat.onTimeRate = BigDecimal.valueOf(onTime * 100.0 / total)
                    .setScale(2, RoundingMode.HALF_UP);
        }
        stat.detail.put("completedOrders", total);
        stat.detail.put("onTimeOrders", onTime);
        return stat;
    }

    /** 退货率 = 退货含税金额 / 采购含税金额 × 100（越低越好） */
    private ReturnStat calcReturn(List<PurchaseOrder> orders, List<PurchaseReturn> returns) {
        ReturnStat stat = new ReturnStat();
        BigDecimal purchase = orders.stream()
                .map(o -> o.getTaxInclusiveAmount() == null ? BigDecimal.ZERO : o.getTaxInclusiveAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returned = returns.stream()
                .map(r -> r.getTaxInclAmount() == null ? BigDecimal.ZERO : r.getTaxInclAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (purchase.compareTo(BigDecimal.ZERO) > 0) {
            stat.returnRate = returned.multiply(BigDecimal.valueOf(100))
                    .divide(purchase, 2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100));
        }
        stat.detail.put("purchaseAmount", purchase.setScale(2, RoundingMode.HALF_UP));
        stat.detail.put("returnAmount", returned.setScale(2, RoundingMode.HALF_UP));
        return stat;
    }

    /** 价格分：本供应商价 vs 全市场同物资均价，价越低分越高 */
    private PriceStat calcPrice(Long supplierId) {
        PriceStat stat = new PriceStat();
        List<SupplierProduct> products = supplierProductMapper.selectList(new LambdaQueryWrapper<SupplierProduct>()
                .eq(SupplierProduct::getSupplierId, supplierId)
                .eq(SupplierProduct::getIsDeleted, 0));
        if (products.isEmpty()) {
            stat.detail.put("sample", 0);
            return stat;
        }
        List<BigDecimal> scores = new ArrayList<>();
        for (SupplierProduct sp : products) {
            if (sp.getMaterialId() == null) {
                continue;
            }
            BigDecimal myPrice = sp.getTaxInclusivePrice();
            if (myPrice == null || myPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            List<SupplierProductPrice> market = supplierProductPriceMapper.selectList(
                    new LambdaQueryWrapper<SupplierProductPrice>()
                            .eq(SupplierProductPrice::getMaterialId, sp.getMaterialId())
                            .eq(SupplierProductPrice::getIsDeleted, 0));
            List<BigDecimal> prices = market.stream()
                    .map(SupplierProductPrice::getTaxInclusivePrice)
                    .filter(p -> p != null && p.compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            if (prices.isEmpty()) {
                prices = List.of(myPrice);
            }
            BigDecimal avg = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(prices.size()), 6, RoundingMode.HALF_UP);
            // 均价/本价 ×100，上限 100
            BigDecimal score = avg.multiply(BigDecimal.valueOf(100))
                    .divide(myPrice, 2, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(100));
            scores.add(score);
        }
        if (!scores.isEmpty()) {
            stat.priceScore = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        }
        stat.detail.put("sample", scores.size());
        stat.detail.put("productCount", products.size());
        return stat;
    }

    private BigDecimal nz(BigDecimal v, BigDecimal def) {
        return v == null ? def : v;
    }
}
