package com.relex.wuzi.web;

import com.relex.wuzi.common.R;
import com.relex.wuzi.mapper.PurchaseStatsMapper;
import com.relex.wuzi.mapper.StockMapper;
import com.relex.wuzi.mapper.StockRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台指标与简单聚合统计
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StockMapper stockMapper;
    private final StockRecordMapper stockRecordMapper;
    private final PurchaseStatsMapper purchaseStatsMapper;

    /**
     * 工作台指标：物资种类、库存总量/金额、供应商数、本月采购金额、入出库量、低库存数
     */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        LocalDateTime monthStartDt = monthStart.atStartOfDay();

        Map<String, Object> data = new HashMap<>();
        data.put("materialKinds", purchaseStatsMapper.countMaterial());
        data.put("stockQty", stockMapper.sumQty());
        data.put("stockAmount", stockMapper.sumAmount());
        data.put("supplierCount", purchaseStatsMapper.countSupplier());
        data.put("monthPurchaseAmount", purchaseStatsMapper.sumMonthAmount(monthStart, nextMonthStart));
        data.put("monthInboundQty", stockRecordMapper.sumInboundQty(monthStartDt));
        data.put("monthOutboundQty", stockRecordMapper.sumOutboundQty(monthStartDt));
        data.put("lowStockCount", stockMapper.countLowStock());
        data.put("stockMaterialKinds", stockMapper.countMaterialWithStock());
        return R.ok(data);
    }

    /**
     * 供应商采购排行（按采购金额）
     */
    @GetMapping("/supplier-purchase-rank")
    public R<List<Map<String, Object>>> supplierPurchaseRank(@RequestParam(defaultValue = "10") int limit) {
        int size = Math.max(1, Math.min(limit, 50));
        return R.ok(purchaseStatsMapper.supplierPurchaseRank(size));
    }

    /**
     * 近期入出库量（默认本月）
     */
    @GetMapping("/inout")
    public R<Map<String, Object>> inout(@RequestParam(required = false) String start,
                                        @RequestParam(required = false) String end) {
        LocalDate s = start != null && !start.isBlank() ? LocalDate.parse(start) : LocalDate.now().withDayOfMonth(1);
        LocalDate e = end != null && !end.isBlank() ? LocalDate.parse(end).plusDays(1) : LocalDate.now().plusDays(1);
        LocalDateTime sDt = s.atStartOfDay();
        LocalDateTime eDt = e.atStartOfDay();

        BigDecimal inQty = stockRecordMapper.sumInboundQty(sDt);
        BigDecimal outQty = stockRecordMapper.sumOutboundQty(sDt);
        List<Map<String, Object>> byBiz = stockRecordMapper.sumQtyByBizType(sDt);

        Map<String, Object> data = new HashMap<>();
        data.put("start", s);
        data.put("end", e.minusDays(1));
        data.put("inboundQty", inQty);
        data.put("outboundQty", outQty);
        data.put("byBizType", byBiz);
        return R.ok(data);
    }
}
