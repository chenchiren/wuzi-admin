package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Stock;
import com.relex.wuzi.domain.StockRecord;
import com.relex.wuzi.mapper.StockMapper;
import com.relex.wuzi.mapper.StockRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 库存查询、库存流水、预警（低库存 safety_stock / 超库存 max_stock）。
 */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockMapper stockMapper;
    private final StockRecordMapper stockRecordMapper;

    @GetMapping
    public R<PageResult<Stock>> page(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) Long warehouseId,
                                     @RequestParam(required = false) Long locationId,
                                     @RequestParam(required = false) Long materialId,
                                     @RequestParam(required = false) String batchNo) {
        LambdaQueryWrapper<Stock> qw = new LambdaQueryWrapper<Stock>()
                .eq(warehouseId != null, Stock::getWarehouseId, warehouseId)
                .eq(locationId != null, Stock::getLocationId, locationId)
                .eq(materialId != null, Stock::getMaterialId, materialId)
                .eq(batchNo != null && !batchNo.isBlank(), Stock::getBatchNo, batchNo)
                .orderByDesc(Stock::getId);
        Page<Stock> p = stockMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/records")
    public R<PageResult<StockRecord>> records(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long warehouseId,
                                              @RequestParam(required = false) Long locationId,
                                              @RequestParam(required = false) Long materialId,
                                              @RequestParam(required = false) String bizType,
                                              @RequestParam(required = false) String batchNo) {
        LambdaQueryWrapper<StockRecord> qw = new LambdaQueryWrapper<StockRecord>()
                .eq(warehouseId != null, StockRecord::getWarehouseId, warehouseId)
                .eq(locationId != null, StockRecord::getLocationId, locationId)
                .eq(materialId != null, StockRecord::getMaterialId, materialId)
                .eq(bizType != null && !bizType.isBlank(), StockRecord::getBizType, bizType)
                .eq(batchNo != null && !batchNo.isBlank(), StockRecord::getBatchNo, batchNo)
                .orderByDesc(StockRecord::getId);
        Page<StockRecord> p = stockRecordMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 库存预警：低库存 qty<=safety_stock，超库存 qty>=max_stock（max_stock>0 时） */
    @GetMapping("/alerts")
    public R<Map<String, Object>> alerts(@RequestParam(required = false) Long warehouseId) {
        // 联表一次查完，避免循环 selectById
        List<Map<String, Object>> low = stockMapper.selectLowStock();
        List<Map<String, Object>> over = stockMapper.selectOverStock();
        if (warehouseId != null) {
            low = low.stream().filter(r -> warehouseId.equals(r.get("warehouseId"))).toList();
            over = over.stream().filter(r -> warehouseId.equals(r.get("warehouseId"))).toList();
        }
        return R.ok(Map.of("lowStock", low, "overStock", over, "lowCount", low.size(), "overCount", over.size()));
    }
}
