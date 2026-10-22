package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Material;
import com.relex.wuzi.domain.StockBatch;
import com.relex.wuzi.domain.StockRecord;
import com.relex.wuzi.domain.StockSerial;
import com.relex.wuzi.domain.Warehouse;
import com.relex.wuzi.domain.WarehouseLocation;
import com.relex.wuzi.mapper.MaterialMapper;
import com.relex.wuzi.mapper.StockBatchMapper;
import com.relex.wuzi.mapper.StockRecordMapper;
import com.relex.wuzi.mapper.StockSerialMapper;
import com.relex.wuzi.mapper.WarehouseLocationMapper;
import com.relex.wuzi.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 序列号 / 批次台账：只读查询与流转记录。
 * 序列号流转按 stock_record.serial_no，批次流水按 stock_record.batch_no。
 */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockLedgerController {

    private final StockSerialMapper stockSerialMapper;
    private final StockBatchMapper stockBatchMapper;
    private final StockRecordMapper stockRecordMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseLocationMapper locationMapper;

    /** 序列号台账分页：序列号、物资、仓库、库位、状态、绑定时间 */
    @GetMapping("/serials")
    public R<PageResult<Map<String, Object>>> serials(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) String serialNo,
                                                      @RequestParam(required = false) Long materialId,
                                                      @RequestParam(required = false) String status) {
        LambdaQueryWrapper<StockSerial> qw = new LambdaQueryWrapper<StockSerial>()
                .eq(materialId != null, StockSerial::getMaterialId, materialId)
                .eq(status != null && !status.isBlank(), StockSerial::getStatus, status)
                .like(serialNo != null && !serialNo.isBlank(), StockSerial::getSerialNo, serialNo)
                .orderByDesc(StockSerial::getId);
        Page<StockSerial> p = stockSerialMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (p.getRecords() != null && !p.getRecords().isEmpty()) {
            Map<Long, String> matNames = loadMaterialNames(p.getRecords().stream()
                    .map(StockSerial::getMaterialId).filter(Objects::nonNull).distinct().toList());
            Map<Long, String> whNames = loadWarehouseNames(p.getRecords().stream()
                    .map(StockSerial::getWarehouseId).filter(Objects::nonNull).distinct().toList());
            Map<Long, String> locNames = loadLocationNames(p.getRecords().stream()
                    .map(StockSerial::getLocationId).filter(Objects::nonNull).distinct().toList());
            for (StockSerial s : p.getRecords()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", s.getId());
                row.put("serialNo", s.getSerialNo());
                row.put("materialId", s.getMaterialId());
                row.put("materialName", s.getMaterialId() == null ? "" : matNames.getOrDefault(s.getMaterialId(), ""));
                row.put("warehouseId", s.getWarehouseId());
                row.put("warehouseName", s.getWarehouseId() == null ? "" : whNames.getOrDefault(s.getWarehouseId(), ""));
                row.put("locationId", s.getLocationId());
                row.put("locationName", s.getLocationId() == null ? "" : locNames.getOrDefault(s.getLocationId(), ""));
                row.put("batchNo", s.getBatchNo());
                row.put("status", s.getStatus());
                row.put("userId", s.getUserId());
                row.put("boundAt", s.getCreatedAt());
                row.put("updatedAt", s.getUpdatedAt());
                rows.add(row);
            }
        }
        return R.ok(PageResult.of(p.getTotal(), rows));
    }

    /** 序列号流转记录（stock_record 按 serial_no） */
    @GetMapping("/serials/{serialNo}/trail")
    public R<List<Map<String, Object>>> serialTrail(@PathVariable String serialNo) {
        List<StockRecord> records = stockRecordMapper.selectList(new LambdaQueryWrapper<StockRecord>()
                .eq(StockRecord::getSerialNo, serialNo)
                .orderByAsc(StockRecord::getId));
        return R.ok(toTrailRows(records));
    }

    /** 批次台账分页：批次号、物资、数量、生产日期、有效期、仓库 */
    @GetMapping("/batches")
    public R<PageResult<Map<String, Object>>> batches(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) String batchNo,
                                                      @RequestParam(required = false) Long materialId,
                                                      @RequestParam(required = false) Long warehouseId) {
        LambdaQueryWrapper<StockBatch> qw = new LambdaQueryWrapper<StockBatch>()
                .eq(materialId != null, StockBatch::getMaterialId, materialId)
                .eq(warehouseId != null, StockBatch::getWarehouseId, warehouseId)
                .like(batchNo != null && !batchNo.isBlank(), StockBatch::getBatchNo, batchNo)
                .orderByDesc(StockBatch::getId);
        Page<StockBatch> p = stockBatchMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (p.getRecords() != null && !p.getRecords().isEmpty()) {
            Map<Long, String> matNames = loadMaterialNames(p.getRecords().stream()
                    .map(StockBatch::getMaterialId).filter(Objects::nonNull).distinct().toList());
            Map<Long, String> whNames = loadWarehouseNames(p.getRecords().stream()
                    .map(StockBatch::getWarehouseId).filter(Objects::nonNull).distinct().toList());
            for (StockBatch b : p.getRecords()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", b.getId());
                row.put("batchNo", b.getBatchNo());
                row.put("materialId", b.getMaterialId());
                row.put("materialName", b.getMaterialId() == null ? "" : matNames.getOrDefault(b.getMaterialId(), ""));
                row.put("warehouseId", b.getWarehouseId());
                row.put("warehouseName", b.getWarehouseId() == null ? "" : whNames.getOrDefault(b.getWarehouseId(), ""));
                row.put("qty", b.getQty());
                row.put("productionDate", b.getProductionDate());
                row.put("expireDate", b.getExpireDate());
                row.put("supplierId", b.getSupplierId());
                row.put("taxInclusivePrice", b.getTaxInclusivePrice());
                row.put("createdAt", b.getCreatedAt());
                row.put("updatedAt", b.getUpdatedAt());
                rows.add(row);
            }
        }
        return R.ok(PageResult.of(p.getTotal(), rows));
    }

    /** 批次流水（stock_record 按 batch_no；id 为 stock_batch.id） */
    @GetMapping("/batches/{id}/trail")
    public R<List<Map<String, Object>>> batchTrail(@PathVariable Long id) {
        StockBatch batch = stockBatchMapper.selectById(id);
        if (batch == null) {
            return R.fail("批次不存在");
        }
        List<StockRecord> records = stockRecordMapper.selectList(new LambdaQueryWrapper<StockRecord>()
                .eq(StockRecord::getBatchNo, batch.getBatchNo())
                .orderByAsc(StockRecord::getId));
        return R.ok(toTrailRows(records));
    }

    private List<Map<String, Object>> toTrailRows(List<StockRecord> records) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return rows;
        }
        Map<Long, String> matNames = loadMaterialNames(records.stream()
                .map(StockRecord::getMaterialId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> whNames = loadWarehouseNames(records.stream()
                .map(StockRecord::getWarehouseId).filter(Objects::nonNull).distinct().toList());
        for (StockRecord r : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("bizType", r.getBizType());
            row.put("bizNo", r.getBizNo());
            row.put("bizId", r.getBizId());
            row.put("materialId", r.getMaterialId());
            row.put("materialName", r.getMaterialId() == null ? "" : matNames.getOrDefault(r.getMaterialId(), ""));
            row.put("warehouseId", r.getWarehouseId());
            row.put("warehouseName", r.getWarehouseId() == null ? "" : whNames.getOrDefault(r.getWarehouseId(), ""));
            row.put("batchNo", r.getBatchNo());
            row.put("serialNo", r.getSerialNo());
            row.put("changeQty", r.getChangeQty());
            row.put("beforeQty", r.getBeforeQty());
            row.put("afterQty", r.getAfterQty());
            row.put("amountSign", r.getAmountSign());
            row.put("taxInclAmount", r.getTaxInclAmount());
            row.put("operatorId", r.getOperatorId());
            row.put("remark", r.getRemark());
            row.put("createdAt", r.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    private Map<Long, String> loadMaterialNames(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (Material m : materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .in(Material::getId, ids))) {
            String label = (m.getMaterialCode() == null ? "" : m.getMaterialCode() + " ")
                    + (m.getMaterialName() == null ? "" : m.getMaterialName());
            map.put(m.getId(), label.trim());
        }
        return map;
    }

    private Map<Long, String> loadWarehouseNames(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (Warehouse w : warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .in(Warehouse::getId, ids))) {
            map.put(w.getId(), w.getWarehouseName());
        }
        return map;
    }

    private Map<Long, String> loadLocationNames(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        for (WarehouseLocation l : locationMapper.selectList(new LambdaQueryWrapper<WarehouseLocation>()
                .in(WarehouseLocation::getId, ids))) {
            map.put(l.getId(), l.getLocationName() != null ? l.getLocationName() : l.getLocationCode());
        }
        return map;
    }
}
