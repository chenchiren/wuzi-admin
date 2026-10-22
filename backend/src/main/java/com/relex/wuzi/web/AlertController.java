package com.relex.wuzi.web;

import com.relex.wuzi.common.R;
import com.relex.wuzi.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存预警：低库存 / 超库存
 * 判定：SUM(stock.qty) vs material.safety_stock / max_stock
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final StockMapper stockMapper;

    @GetMapping
    public R<Map<String, Object>> all() {
        List<Map<String, Object>> low = stockMapper.selectLowStock();
        List<Map<String, Object>> over = stockMapper.selectOverStock();
        Map<String, Object> data = new HashMap<>();
        data.put("lowStock", low);
        data.put("overStock", over);
        data.put("lowStockCount", low.size());
        data.put("overStockCount", over.size());
        return R.ok(data);
    }

    @GetMapping("/low-stock")
    public R<List<Map<String, Object>>> lowStock() {
        return R.ok(stockMapper.selectLowStock());
    }

    @GetMapping("/over-stock")
    public R<List<Map<String, Object>>> overStock() {
        return R.ok(stockMapper.selectOverStock());
    }
}
