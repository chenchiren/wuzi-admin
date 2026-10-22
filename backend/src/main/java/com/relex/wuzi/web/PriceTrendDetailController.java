package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.PurchaseOrder;
import com.relex.wuzi.domain.PurchaseOrderItem;
import com.relex.wuzi.domain.SupplierProductPrice;
import com.relex.wuzi.mapper.PurchaseOrderItemMapper;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.SupplierProductPriceMapper;
import com.relex.wuzi.service.TraceScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 价格趋势明细：按月聚合序列 + 环比涨跌幅。
 * 数据源：supplier_product_price + purchase_order_item（与 PriceTrendController 同源，按月重采样）。
 */
@RestController
@RequestMapping("/api/price-trend-details")
@RequiredArgsConstructor
public class PriceTrendDetailController {

    private final SupplierProductPriceMapper priceMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PurchaseOrderMapper orderMapper;

    @GetMapping
    public R<Map<String, Object>> monthly(@RequestParam(required = false) Long materialId,
                                          @RequestParam(required = false) Long supplierProductId,
                                          @RequestParam(required = false) Integer months,
                                          @RequestParam(required = false) Boolean hidePrice,
                                          @RequestAttribute(value = "roles", required = false) List<String> roles) {
        if (materialId == null && supplierProductId == null) {
            throw new BizException("materialId 或 supplierProductId 至少提供一个");
        }
        boolean hide = TraceScanService.shouldHidePrice(hidePrice, roles);
        int window = months == null || months <= 0 ? 24 : Math.min(months, 60);
        LocalDate from = YearMonth.now().minusMonths(window - 1L).atDay(1);

        // month -> prices
        Map<YearMonth, List<BigDecimal>> buckets = new TreeMap<>();

        List<SupplierProductPrice> priceRows = priceMapper.selectList(new LambdaQueryWrapper<SupplierProductPrice>()
                .eq(materialId != null, SupplierProductPrice::getMaterialId, materialId)
                .eq(supplierProductId != null, SupplierProductPrice::getSupplierProductId, supplierProductId)
                .eq(SupplierProductPrice::getIsDeleted, 0)
                .ge(SupplierProductPrice::getEffectiveFrom, from)
                .orderByAsc(SupplierProductPrice::getEffectiveFrom));
        for (SupplierProductPrice p : priceRows) {
            if (p.getEffectiveFrom() == null || p.getTaxInclusivePrice() == null) {
                continue;
            }
            buckets.computeIfAbsent(YearMonth.from(p.getEffectiveFrom()), k -> new ArrayList<>())
                    .add(p.getTaxInclusivePrice());
        }

        List<PurchaseOrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(materialId != null, PurchaseOrderItem::getMaterialId, materialId)
                .eq(supplierProductId != null, PurchaseOrderItem::getSupplierProductId, supplierProductId)
                .orderByAsc(PurchaseOrderItem::getId));
        for (PurchaseOrderItem item : orderItems) {
            if (item.getOrderId() == null || item.getTaxInclusivePrice() == null) {
                continue;
            }
            PurchaseOrder order = orderMapper.selectById(item.getOrderId());
            if (order == null || order.getOrderDate() == null) {
                continue;
            }
            LocalDate d = order.getOrderDate();
            if (d.isBefore(from)) {
                continue;
            }
            buckets.computeIfAbsent(YearMonth.from(d), k -> new ArrayList<>())
                    .add(item.getTaxInclusivePrice());
        }

        List<Map<String, Object>> series = new ArrayList<>();
        BigDecimal prevAvg = null;
        for (Map.Entry<YearMonth, List<BigDecimal>> e : buckets.entrySet()) {
            List<BigDecimal> prices = e.getValue();
            BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(prices.size()), 6, RoundingMode.HALF_UP);
            BigDecimal min = prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            BigDecimal max = prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("month", e.getKey().toString());
            point.put("sampleCount", prices.size());
            if (!hide) {
                point.put("avgPrice", avg.setScale(4, RoundingMode.HALF_UP));
                point.put("minPrice", min.setScale(4, RoundingMode.HALF_UP));
                point.put("maxPrice", max.setScale(4, RoundingMode.HALF_UP));
                if (prevAvg != null && prevAvg.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal changeRate = avg.subtract(prevAvg)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(prevAvg, 2, RoundingMode.HALF_UP);
                    point.put("changeRatePercent", changeRate);
                    point.put("changeAmount", avg.subtract(prevAvg).setScale(4, RoundingMode.HALF_UP));
                    point.put("direction", changeRate.compareTo(BigDecimal.ZERO) > 0 ? "UP"
                            : (changeRate.compareTo(BigDecimal.ZERO) < 0 ? "DOWN" : "FLAT"));
                } else {
                    point.put("changeRatePercent", null);
                    point.put("changeAmount", null);
                    point.put("direction", "FLAT");
                }
            }
            series.add(point);
            prevAvg = avg;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("materialId", materialId);
        data.put("supplierProductId", supplierProductId);
        data.put("hidePrice", hide);
        data.put("windowMonths", window);
        data.put("series", series);
        data.put("count", series.size());
        return R.ok(data);
    }
}
