package com.relex.wuzi.web;

import com.relex.wuzi.annotation.RequirePerm;

import com.relex.wuzi.common.BillNoUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import com.relex.wuzi.service.ApprovalService;
import com.relex.wuzi.service.FlowApprovalService;
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
 * 库存盘点。依赖 inventory_check / inventory_check_item（表已建，见 sql/wuzi_full.sql）。
 * 确认时按账面/实盘差异调账（盘盈入库 / 盘亏出库，红冲 amount_sign=-1）。
 */
@RestController
@RequestMapping("/api/inventory/checks")
@RequiredArgsConstructor
public class InventoryCheckController {

    private final InventoryCheckMapper checkMapper;
    private final InventoryCheckItemMapper checkItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final MaterialMapper materialMapper;
    private final StockService stockService;
    private final FlowApprovalService flowApprovalService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class CreateItem {
        private Long materialId;
        private String batchNo;
        private Long locationId;
        private BigDecimal bookQty;
        private BigDecimal actualQty;
        private BigDecimal taxInclPrice;
        private String remark;
    }

    @Data
    public static class CreateReq {
        private Long warehouseId;
        private String remark;
        private List<CreateItem> items;
    }

    @GetMapping
    public R<PageResult<InventoryCheck>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String status) {
        LambdaQueryWrapper<InventoryCheck> qw = new LambdaQueryWrapper<InventoryCheck>()
                .eq(status != null && !status.isBlank(), InventoryCheck::getStatus, status)
                .orderByDesc(InventoryCheck::getId);
        Page<InventoryCheck> p = checkMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        InventoryCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw new BizException("盘点单不存在");
        }
        List<InventoryCheckItem> items = checkItemMapper.selectList(new LambdaQueryWrapper<InventoryCheckItem>()
                .eq(InventoryCheckItem::getCheckId, id));
        return R.ok(Map.of("check", check, "items", items));
    }

    @RequirePerm("wh:inventory:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req, @RequestAttribute("userId") Long userId) {
        if (req.getWarehouseId() == null || warehouseMapper.selectById(req.getWarehouseId()) == null) {
            throw new BizException("仓库不存在");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("盘点明细不能为空");
        }

        InventoryCheck check = new InventoryCheck();
        check.setCheckNo(BillNoUtil.next("IC"));
        check.setWarehouseId(req.getWarehouseId());
        check.setOperatorId(userId);
        check.setCheckTime(LocalDateTime.now());
        check.setStatus("DRAFT");
        check.setRemark(req.getRemark());
        check.setIsDeleted(0);
        checkMapper.insert(check);

        List<InventoryCheckItem> items = new ArrayList<>();
        for (CreateItem it : req.getItems()) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            String batchNo = StockService.normalizeBatch(it.getBatchNo());
            BigDecimal bookQty = it.getBookQty();
            if (bookQty == null) {
                bookQty = stockService.availableQty(req.getWarehouseId(), it.getMaterialId(), batchNo);
            }
            BigDecimal actual = it.getActualQty() != null ? it.getActualQty() : bookQty;
            BigDecimal diff = actual.subtract(bookQty);

            InventoryCheckItem item = new InventoryCheckItem();
            item.setCheckId(check.getId());
            item.setMaterialId(it.getMaterialId());
            item.setBatchNo(batchNo.isEmpty() ? null : batchNo);
            item.setLocationId(it.getLocationId());
            item.setBookQty(bookQty);
            item.setActualQty(actual);
            item.setDiffQty(diff);
            if (it.getTaxInclPrice() != null) {
                item.setTaxInclPrice(it.getTaxInclPrice());
            } else if (material.getRefPrice() != null) {
                item.setTaxInclPrice(material.getRefPrice());
            } else {
                item.setTaxInclPrice(BigDecimal.ZERO);
            }
            item.setRemark(it.getRemark());
            checkItemMapper.insert(item);
            items.add(item);
        }
        return R.ok(Map.of("check", check, "items", items));
    }

    /** 提交盘点进入审批流 */
    @RequirePerm("wh:inventory:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<InventoryCheck> submit(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        InventoryCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw new BizException("盘点单不存在");
        }
        if (!"DRAFT".equals(check.getStatus()) && !"REJECTED".equals(check.getStatus())) {
            throw new BizException("当前状态不可提交: " + check.getStatus());
        }
        flowApprovalService.start(ApprovalService.BIZ_INVENTORY, ApprovalService.BIZ_INVENTORY,
                id, check.getCheckNo(), userId, "盘点申请-" + check.getCheckNo(),
                status -> {
                    check.setStatus(status);
                    check.setUpdatedAt(LocalDateTime.now());
                    checkMapper.updateById(check);
                });
        return R.ok(check);
    }

    /** 确认盘点：差异调账。盘盈 increase（sign=1），盘亏 decrease 或红冲（sign=-1） */
    @RequirePerm("wh:inventory:confirm")
    @PostMapping("/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> confirm(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        InventoryCheck check = checkMapper.selectById(id);
        if (check == null) {
            throw new BizException("盘点单不存在");
        }
        if (!ApprovalService.ST_APPROVED.equals(check.getStatus())) {
            throw new BizException("仅审批通过的盘点单可确认，当前: " + check.getStatus());
        }
        List<InventoryCheckItem> items = checkItemMapper.selectList(new LambdaQueryWrapper<InventoryCheckItem>()
                .eq(InventoryCheckItem::getCheckId, id));
        if (items.isEmpty()) {
            throw new BizException("盘点明细为空");
        }

        for (InventoryCheckItem item : items) {
            BigDecimal diff = item.getDiffQty() == null ? BigDecimal.ZERO : item.getDiffQty();
            if (diff.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            String batchNo = StockService.normalizeBatch(item.getBatchNo());
            BigDecimal price = item.getTaxInclPrice() != null ? item.getTaxInclPrice() : BigDecimal.ZERO;
            BigDecimal amount = price.multiply(diff.abs()).setScale(2, java.math.RoundingMode.HALF_UP);

            StockService.StockChange change = new StockService.StockChange();
            change.setBizType(diff.compareTo(BigDecimal.ZERO) > 0 ? "CHECK_GAIN" : "CHECK_LOSS");
            change.setBizNo(check.getCheckNo());
            change.setBizId(check.getId());
            change.setWarehouseId(check.getWarehouseId());
            change.setLocationId(item.getLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(batchNo);
            change.setChangeQty(diff.abs());
            change.setTaxInclAmount(amount);
            change.setCostTaxIncl(amount);
            change.setTaxInclPrice(price);
            change.setOperatorId(userId);
            change.setRemark("盘点调账: 账面" + item.getBookQty() + " 实盘" + item.getActualQty());

            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                stockService.increase(change);
            } else {
                change.setChangeQty(diff);
                stockService.redFlush(change);
            }
        }

        check.setStatus("CONFIRMED");
        check.setUpdatedAt(LocalDateTime.now());
        checkMapper.updateById(check);
        return R.ok(Map.of("check", checkMapper.selectById(id), "items", items));
    }
}
