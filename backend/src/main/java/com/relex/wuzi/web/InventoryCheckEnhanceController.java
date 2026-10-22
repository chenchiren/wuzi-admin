package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.BillNoUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import com.relex.wuzi.service.StockService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 盘点增强：抽盘（按分类/比例）、回填账面、库位向导、导出 CSV。
 * 与 InventoryCheckController 共享 /api/inventory/checks 前缀，仅新增增强端点。
 */
@RestController
@RequestMapping("/api/inventory/checks")
@RequiredArgsConstructor
public class InventoryCheckEnhanceController {

    private final InventoryCheckMapper checkMapper;
    private final InventoryCheckItemMapper checkItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseLocationMapper locationMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final StockMapper stockMapper;
    private final StockService stockService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class SpotReq {
        private Long warehouseId;
        /** 分类 ID，可空（空=全仓库） */
        private Long categoryId;
        /** 抽盘比例 0-100，例如 20 表示抽 20% */
        private BigDecimal sampleRatio;
        /** 固定抽样条数，优先于比例 */
        private Integer sampleCount;
        private String remark;
    }

    @Data
    public static class FillBookReq {
        /** 可选：只回填指定明细 */
        private List<Long> itemIds;
    }

    /**
     * 抽盘：按分类/比例从库存生成盘点任务行。
     */
    @RequirePerm("wh:inventory:create")
    @PostMapping("/spot")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> spot(@RequestBody SpotReq req,
                                       @RequestAttribute("userId") Long userId) {
        if (req.getWarehouseId() == null || warehouseMapper.selectById(req.getWarehouseId()) == null) {
            throw new BizException("仓库不存在");
        }
        BigDecimal ratio = req.getSampleRatio();
        Integer sampleCount = req.getSampleCount();
        if ((ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0)
                && (sampleCount == null || sampleCount <= 0)) {
            throw new BizException("请指定抽盘比例或抽样条数");
        }

        List<Long> materialIds = resolveMaterialIds(req.getCategoryId());
        List<Stock> pool = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getWarehouseId, req.getWarehouseId())
                .gt(Stock::getQty, 0)
                .in(materialIds != null && !materialIds.isEmpty(), Stock::getMaterialId, materialIds));
        if (pool.isEmpty()) {
            throw new BizException("该范围无可盘库存");
        }

        List<Stock> picked;
        if (sampleCount != null && sampleCount > 0) {
            int n = Math.min(sampleCount, pool.size());
            picked = new ArrayList<>(pool);
            Collections.shuffle(picked);
            picked = picked.subList(0, n);
        } else {
            int n = (int) Math.ceil(pool.size() * ratio.doubleValue() / 100.0);
            n = Math.max(1, Math.min(n, pool.size()));
            picked = new ArrayList<>(pool);
            Collections.shuffle(picked);
            picked = picked.subList(0, n);
        }

        InventoryCheck check = new InventoryCheck();
        check.setCheckNo(BillNoUtil.next("ICS"));
        check.setWarehouseId(req.getWarehouseId());
        check.setOperatorId(userId);
        check.setCheckTime(LocalDateTime.now());
        check.setStatus("DRAFT");
        String tag = "抽盘";
        if (req.getCategoryId() != null) {
            MaterialCategory cat = categoryMapper.selectById(req.getCategoryId());
            tag += (cat != null ? "-" + cat.getCategoryName() : "");
        }
        check.setRemark(tag + (req.getRemark() != null ? " | " + req.getRemark() : ""));
        check.setIsDeleted(0);
        checkMapper.insert(check);

        List<InventoryCheckItem> items = new ArrayList<>();
        for (Stock s : picked) {
            String batchNo = StockService.normalizeBatch(s.getBatchNo());
            BigDecimal bookQty = s.getQty() == null ? BigDecimal.ZERO : s.getQty();
            Material material = materialMapper.selectById(s.getMaterialId());
            InventoryCheckItem item = new InventoryCheckItem();
            item.setCheckId(check.getId());
            item.setMaterialId(s.getMaterialId());
            item.setBatchNo(batchNo.isEmpty() ? null : batchNo);
            item.setLocationId(s.getLocationId());
            item.setBookQty(bookQty);
            item.setActualQty(null);
            item.setDiffQty(BigDecimal.ZERO);
            item.setTaxInclPrice(material != null && material.getRefPrice() != null
                    ? material.getRefPrice() : BigDecimal.ZERO);
            checkItemMapper.insert(item);
            items.add(item);
        }
        return R.ok(Map.of("check", check, "items", items, "poolSize", pool.size()));
    }

    /**
     * 回填账面：按当前库存重算 book_qty / diff_qty。
     */
    @RequirePerm("wh:inventory:update")
    @PostMapping("/{id}/fill-book")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> fillBook(@PathVariable Long id,
                                           @RequestBody(required = false) FillBookReq req) {
        InventoryCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw new BizException("盘点单不存在");
        }
        if (!"DRAFT".equals(check.getStatus()) && !"REJECTED".equals(check.getStatus())) {
            throw new BizException("仅草稿/驳回状态可回填账面，当前: " + check.getStatus());
        }
        List<InventoryCheckItem> items = checkItemMapper.selectList(new LambdaQueryWrapper<InventoryCheckItem>()
                .eq(InventoryCheckItem::getCheckId, id));
        Set<Long> filter = req != null && req.getItemIds() != null ? new HashSet<>(req.getItemIds()) : null;

        for (InventoryCheckItem item : items) {
            if (filter != null && !filter.isEmpty() && !filter.contains(item.getId())) {
                continue;
            }
            String batchNo = StockService.normalizeBatch(item.getBatchNo());
            BigDecimal book = stockService.availableQty(check.getWarehouseId(), item.getMaterialId(), batchNo);
            item.setBookQty(book);
            BigDecimal actual = item.getActualQty() != null ? item.getActualQty() : book;
            item.setDiffQty(actual.subtract(book));
            checkItemMapper.updateById(item);
        }
        check.setUpdatedAt(LocalDateTime.now());
        checkMapper.updateById(check);
        return R.ok(Map.of("check", checkMapper.selectById(id), "items", items));
    }

    /**
     * 库位向导：按库位列出待盘物资，辅助逐库位盘点。
     */
    @GetMapping("/location-wizard")
    public R<Map<String, Object>> locationWizard(@RequestParam Long warehouseId,
                                                 @RequestParam(required = false) Long locationId) {
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new BizException("仓库不存在");
        }
        List<WarehouseLocation> locations = locationMapper.selectList(new LambdaQueryWrapper<WarehouseLocation>()
                .eq(WarehouseLocation::getWarehouseId, warehouseId)
                .eq(WarehouseLocation::getStatus, 1)
                .orderByAsc(WarehouseLocation::getLocationCode));

        List<Map<String, Object>> steps = new ArrayList<>();
        int index = 0;
        int total = locations.size();
        for (WarehouseLocation loc : locations) {
            List<Stock> stocks = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                    .eq(Stock::getWarehouseId, warehouseId)
                    .eq(Stock::getLocationId, loc.getId())
                    .gt(Stock::getQty, 0));
            index++;
            Map<String, Object> step = new HashMap<>();
            step.put("locationId", loc.getId());
            step.put("locationCode", loc.getLocationCode());
            step.put("locationName", loc.getLocationName());
            step.put("area", loc.getArea());
            step.put("stepNo", index);
            step.put("stepTotal", total);
            step.put("stockCount", stocks.size());
            List<Map<String, Object>> lines = new ArrayList<>();
            for (Stock s : stocks) {
                Material m = materialMapper.selectById(s.getMaterialId());
                Map<String, Object> line = new HashMap<>();
                line.put("stockId", s.getId());
                line.put("materialId", s.getMaterialId());
                line.put("materialCode", m != null ? m.getMaterialCode() : null);
                line.put("materialName", m != null ? m.getMaterialName() : null);
                line.put("batchNo", s.getBatchNo());
                line.put("qty", s.getQty());
                lines.add(line);
            }
            step.put("lines", lines);
            steps.add(step);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("warehouseId", warehouseId);
        result.put("steps", steps);
        if (locationId != null) {
            result.put("current", steps.stream()
                    .filter(s -> locationId.equals(s.get("locationId")))
                    .findFirst().orElse(null));
        }
        return R.ok(result);
    }

    /**
     * 导出 CSV（简化）：盘点明细导出。
     */
    @GetMapping("/{id}/export")
    public R<String> exportCsv(@PathVariable Long id) {
        InventoryCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw new BizException("盘点单不存在");
        }
        List<InventoryCheckItem> items = checkItemMapper.selectList(new LambdaQueryWrapper<InventoryCheckItem>()
                .eq(InventoryCheckItem::getCheckId, id));
        StringBuilder sb = new StringBuilder();
        sb.append("盘点单号,物资ID,批次,库位,账面数,实盘数,差异数,含税单价,备注\n");
        for (InventoryCheckItem it : items) {
            sb.append(csv(check.getCheckNo())).append(',')
                    .append(it.getMaterialId()).append(',')
                    .append(csv(it.getBatchNo())).append(',')
                    .append(it.getLocationId() == null ? "" : it.getLocationId()).append(',')
                    .append(it.getBookQty() == null ? "" : it.getBookQty()).append(',')
                    .append(it.getActualQty() == null ? "" : it.getActualQty()).append(',')
                    .append(it.getDiffQty() == null ? "" : it.getDiffQty()).append(',')
                    .append(it.getTaxInclPrice() == null ? "" : it.getTaxInclPrice()).append(',')
                    .append(csv(it.getRemark()))
                    .append('\n');
        }
        return R.ok(sb.toString());
    }

    /** 收集分类（含子分类）下的物资 ID；categoryId 为空返回 null 表示不限制 */
    private List<Long> resolveMaterialIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Set<Long> catIds = new HashSet<>();
        catIds.add(categoryId);
        List<MaterialCategory> all = categoryMapper.selectList(null);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (MaterialCategory c : all) {
                if (c.getParentId() != null && catIds.contains(c.getParentId()) && catIds.add(c.getId())) {
                    grew = true;
                }
            }
        }
        List<Material> mats = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .in(Material::getCategoryId, catIds));
        return mats.stream().map(Material::getId).toList();
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
