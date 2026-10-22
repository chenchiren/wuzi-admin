package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Warehouse;
import com.relex.wuzi.domain.WarehouseLocation;
import com.relex.wuzi.mapper.WarehouseLocationMapper;
import com.relex.wuzi.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseMapper warehouseMapper;
    private final WarehouseLocationMapper locationMapper;

    @GetMapping
    public R<PageResult<Warehouse>> page(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Warehouse> qw = new LambdaQueryWrapper<Warehouse>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Warehouse::getWarehouseName, keyword)
                        .or().like(Warehouse::getWarehouseCode, keyword))
                .orderByDesc(Warehouse::getId);
        Page<Warehouse> p = warehouseMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public R<List<Warehouse>> list() {
        return R.ok(warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .orderByAsc(Warehouse::getWarehouseCode)));
    }

    @GetMapping("/{id}")
    public R<Warehouse> get(@PathVariable Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) {
            throw new BizException("仓库不存在");
        }
        return R.ok(w);
    }

    @RequirePerm("wh:warehouse:create")
    @PostMapping
    public R<Warehouse> create(@RequestBody Warehouse warehouse) {
        Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getWarehouseCode, warehouse.getWarehouseCode()));
        if (count != null && count > 0) {
            throw new BizException("仓库编码已存在");
        }
        warehouse.setId(null);
        warehouse.setIsDeleted(0);
        if (warehouse.getStatus() == null) {
            warehouse.setStatus(1);
        }
        warehouseMapper.insert(warehouse);
        return R.ok(warehouse);
    }

    @RequirePerm("wh:warehouse:update")
    @PutMapping("/{id}")
    public R<Warehouse> update(@PathVariable Long id, @RequestBody Warehouse warehouse) {
        Warehouse exist = warehouseMapper.selectById(id);
        if (exist == null) {
            throw new BizException("仓库不存在");
        }
        warehouse.setId(id);
        if (warehouse.getWarehouseCode() != null && !warehouse.getWarehouseCode().equals(exist.getWarehouseCode())) {
            Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                    .eq(Warehouse::getWarehouseCode, warehouse.getWarehouseCode()));
            if (count != null && count > 0) {
                throw new BizException("仓库编码已存在");
            }
        }
        warehouseMapper.updateById(warehouse);
        return R.ok(warehouseMapper.selectById(id));
    }

    @RequirePerm("wh:warehouse:delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Warehouse exist = warehouseMapper.selectById(id);
        if (exist == null) {
            throw new BizException("仓库不存在");
        }
        exist.setIsDeleted(1);
        warehouseMapper.updateById(exist);
        return R.ok();
    }

    @GetMapping("/{warehouseId}/locations")
    public R<List<WarehouseLocation>> listLocations(@PathVariable Long warehouseId) {
        return R.ok(locationMapper.selectList(new LambdaQueryWrapper<WarehouseLocation>()
                .eq(WarehouseLocation::getWarehouseId, warehouseId)
                .orderByAsc(WarehouseLocation::getLocationCode)));
    }

    @RequirePerm("wh:warehouse:create")
    @PostMapping("/{warehouseId}/locations")
    public R<WarehouseLocation> createLocation(@PathVariable Long warehouseId,
                                               @RequestBody WarehouseLocation location) {
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new BizException("仓库不存在");
        }
        Long count = locationMapper.selectCount(new LambdaQueryWrapper<WarehouseLocation>()
                .eq(WarehouseLocation::getWarehouseId, warehouseId)
                .eq(WarehouseLocation::getLocationCode, location.getLocationCode()));
        if (count != null && count > 0) {
            throw new BizException("库位编码已存在");
        }
        location.setId(null);
        location.setWarehouseId(warehouseId);
        location.setIsDeleted(0);
        if (location.getStatus() == null) {
            location.setStatus(1);
        }
        locationMapper.insert(location);
        return R.ok(location);
    }

    @RequirePerm("wh:warehouse:update")
    @PutMapping("/locations/{locationId}")
    public R<WarehouseLocation> updateLocation(@PathVariable Long locationId,
                                               @RequestBody WarehouseLocation location) {
        WarehouseLocation exist = locationMapper.selectById(locationId);
        if (exist == null) {
            throw new BizException("库位不存在");
        }
        location.setId(locationId);
        locationMapper.updateById(location);
        return R.ok(locationMapper.selectById(locationId));
    }

    @RequirePerm("wh:warehouse:delete")
    @DeleteMapping("/locations/{locationId}")
    public R<Void> deleteLocation(@PathVariable Long locationId) {
        WarehouseLocation exist = locationMapper.selectById(locationId);
        if (exist == null) {
            throw new BizException("库位不存在");
        }
        exist.setIsDeleted(1);
        locationMapper.updateById(exist);
        return R.ok();
    }
}
