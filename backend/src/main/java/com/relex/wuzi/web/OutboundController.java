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
 * 出库创建 / 确认。确认时校验可用量（qty - frozen_qty）并扣减 + 写流水。
 */
@RestController
@RequestMapping("/api/outbound/orders")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderItemMapper outboundItemMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final StockService stockService;
    private final StockMapper stockMapper;
    private final FlowApprovalService flowApprovalService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class CreateItem {
        private Long materialId;
        private String batchNo;
        private Long locationId;
        private BigDecimal qty;
        private String serialNo;
    }

    @Data
    public static class CreateReq {
        private Long warehouseId;
        private String outboundType;
        private Long applyId;
        private Long deptId;
        private Long userId;
        private String remark;
        private List<CreateItem> items;
    }

    @GetMapping
    public R<PageResult<OutboundOrder>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Long warehouseId) {
        LambdaQueryWrapper<OutboundOrder> qw = new LambdaQueryWrapper<OutboundOrder>()
                .eq(status != null && !status.isBlank(), OutboundOrder::getStatus, status)
                .eq(warehouseId != null, OutboundOrder::getWarehouseId, warehouseId)
                .orderByDesc(OutboundOrder::getId);
        Page<OutboundOrder> p = outboundOrderMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        List<OutboundOrderItem> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, id));
        return R.ok(Map.of("order", order, "items", items));
    }

    @RequirePerm("wh:outbound:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req, @RequestAttribute("userId") Long userId) {
        if (req.getWarehouseId() == null || warehouseMapper.selectById(req.getWarehouseId()) == null) {
            throw new BizException("仓库不存在");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("出库明细不能为空");
        }

        OutboundOrder order = new OutboundOrder();
        order.setOutboundNo(BillNoUtil.next("OUT"));
        order.setOutboundType(req.getOutboundType() != null ? req.getOutboundType() : "USE");
        order.setWarehouseId(req.getWarehouseId());
        order.setApplyId(req.getApplyId());
        order.setDeptId(req.getDeptId());
        order.setUserId(req.getUserId() != null ? req.getUserId() : userId);
        order.setOperatorId(userId);
        order.setOutboundTime(LocalDateTime.now());
        order.setStatus("DRAFT");
        order.setRemark(req.getRemark());
        order.setIsDeleted(0);
        outboundOrderMapper.insert(order);

        List<OutboundOrderItem> items = new ArrayList<>();
        for (CreateItem it : req.getItems()) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            if (it.getQty() == null || it.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("出库数量必须大于0");
            }
            String batchNo = StockService.normalizeBatch(it.getBatchNo());
            Stock stock = stockService.findStock(req.getWarehouseId(), it.getMaterialId(), batchNo);
            BigDecimal price = BigDecimal.ZERO;
            BigDecimal rate = material.getRefTaxRate();
            if (stock != null) {
                if (stock.getLastTaxInclPrice() != null) {
                    price = stock.getLastTaxInclPrice();
                }
                if (stock.getLastTaxRate() != null) {
                    rate = stock.getLastTaxRate();
                }
            }
            if (price.compareTo(BigDecimal.ZERO) == 0 && material.getRefPrice() != null) {
                price = material.getRefPrice();
            }

            OutboundOrderItem item = new OutboundOrderItem();
            item.setOutboundId(order.getId());
            item.setMaterialId(it.getMaterialId());
            item.setBatchNo(batchNo.isEmpty() ? null : batchNo);
            item.setLocationId(it.getLocationId());
            item.setQty(it.getQty());
            item.setActualQty(BigDecimal.ZERO);
            item.setTaxInclPrice(price);
            item.setTaxRate(rate);
            outboundItemMapper.insert(item);
            items.add(item);
        }
        return R.ok(Map.of("order", order, "items", items));
    }

    /** 作废草稿出库单（未扣库存） */
    @RequirePerm("wh:outbound:cancel")
    @PostMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<OutboundOrder> cancel(@PathVariable Long id) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BizException("仅草稿/已驳回可作废，当前: " + order.getStatus());
        }
        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        outboundOrderMapper.updateById(order);
        return R.ok(order);
    }

    /** 提交出库进入审批流 */
    @RequirePerm("wh:outbound:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<OutboundOrder> submit(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BizException("当前状态不可提交: " + order.getStatus());
        }
        List<OutboundOrderItem> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, id));
        if (items.isEmpty()) {
            throw new BizException("出库明细为空");
        }
        flowApprovalService.start(ApprovalService.BIZ_OUTBOUND, ApprovalService.BIZ_OUTBOUND,
                id, order.getOutboundNo(), userId, "出库申请-" + order.getOutboundNo(),
                status -> {
                    order.setStatus(status);
                    order.setUpdatedAt(LocalDateTime.now());
                    outboundOrderMapper.updateById(order);
                });
        return R.ok(order);
    }

    @RequirePerm("wh:outbound:confirm")
    @PostMapping("/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> confirm(@PathVariable Long id,
                                          @RequestParam(required = false) BigDecimal actualQtyOverride,
                                          @RequestAttribute("userId") Long userId) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        if (!ApprovalService.ST_APPROVED.equals(order.getStatus())) {
            throw new BizException("仅审批通过的出库单可确认出库，当前: " + order.getStatus());
        }
        List<OutboundOrderItem> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, id));
        if (items.isEmpty()) {
            throw new BizException("出库明细为空");
        }

        for (OutboundOrderItem item : items) {
            BigDecimal outQty = item.getActualQty() != null && item.getActualQty().compareTo(BigDecimal.ZERO) > 0
                    ? item.getActualQty()
                    : (actualQtyOverride != null ? actualQtyOverride : item.getQty());
            if (outQty == null || outQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("出库数量必须大于0");
            }
            String batchNo = StockService.normalizeBatch(item.getBatchNo());
            BigDecimal available = stockService.availableQty(order.getWarehouseId(), item.getMaterialId(), batchNo);
            if (available.compareTo(outQty) < 0) {
                throw new BizException("可用库存不足: 物料" + item.getMaterialId()
                        + " 可用" + available + "，需" + outQty);
            }

            Stock stock = stockService.findStock(order.getWarehouseId(), item.getMaterialId(), batchNo);
            BigDecimal taxInclPrice = item.getTaxInclPrice() != null ? item.getTaxInclPrice() : BigDecimal.ZERO;
            BigDecimal taxInclAmount = taxInclPrice.multiply(outQty).setScale(2, java.math.RoundingMode.HALF_UP);

            StockService.StockChange change = new StockService.StockChange();
            change.setBizType("OUTBOUND");
            change.setBizNo(order.getOutboundNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getWarehouseId());
            change.setLocationId(item.getLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(batchNo);
            change.setChangeQty(outQty);
            change.setTaxInclAmount(taxInclAmount);
            change.setTaxRate(item.getTaxRate());
            change.setTaxInclPrice(taxInclPrice);
            change.setOperatorId(userId);
            change.setRemark("出库确认");
            if (stock != null) {
                BigDecimal unitCostIncl = nvl(stock.getCostTaxIncl());
                BigDecimal qtyBefore = nvl(stock.getQty());
                BigDecimal costIncl = qtyBefore.compareTo(BigDecimal.ZERO) > 0
                        ? unitCostIncl.multiply(outQty).divide(qtyBefore, 2, java.math.RoundingMode.HALF_UP)
                        : taxInclAmount;
                change.setCostTaxIncl(costIncl.negate());
                BigDecimal unitCostExcl = nvl(stock.getCostTaxExcl());
                BigDecimal costExcl = qtyBefore.compareTo(BigDecimal.ZERO) > 0
                        ? unitCostExcl.multiply(outQty).divide(qtyBefore, 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                change.setCostTaxExcl(costExcl.negate());
            }
            stockService.decrease(change);

            Material material = materialMapper.selectById(item.getMaterialId());
            if (material != null && material.getSerialManaged() != null && material.getSerialManaged() == 1) {
                stockService.releaseSerial(item.getBatchNo());
            }

            item.setActualQty(outQty);
            outboundItemMapper.updateById(item);
        }

        order.setStatus("CONFIRMED");
        order.setUpdatedAt(LocalDateTime.now());
        outboundOrderMapper.updateById(order);
        return R.ok(Map.of("order", outboundOrderMapper.selectById(id), "items", items));
    }

    /** 红冲出库：冲销已确认出库，amount_sign=-1，库存回补 */
    @RequirePerm("wh:outbound:red-flush")
    @PostMapping("/{id}/red-flush")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> redFlush(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        if (!"CONFIRMED".equals(order.getStatus())) {
            throw new BizException("仅已确认出库单可红冲，当前: " + order.getStatus());
        }
        List<OutboundOrderItem> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, id));
        for (OutboundOrderItem item : items) {
            BigDecimal qty = item.getActualQty() != null && item.getActualQty().compareTo(BigDecimal.ZERO) > 0
                    ? item.getActualQty() : item.getQty();
            String batchNo = StockService.normalizeBatch(item.getBatchNo());
            BigDecimal taxInclPrice = item.getTaxInclPrice() != null ? item.getTaxInclPrice() : BigDecimal.ZERO;
            StockService.StockChange change = new StockService.StockChange();
            change.setBizType("OUTBOUND_RED");
            change.setBizNo(order.getOutboundNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getWarehouseId());
            change.setLocationId(item.getLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(batchNo);
            change.setChangeQty(qty);
            change.setTaxInclAmount(taxInclPrice.multiply(qty).setScale(2, java.math.RoundingMode.HALF_UP));
            change.setTaxRate(item.getTaxRate());
            change.setTaxInclPrice(taxInclPrice);
            change.setOperatorId(userId);
            change.setRemark("出库红冲回补");
            stockService.redFlush(change);
        }
        order.setStatus("RED_FLUSHED");
        order.setUpdatedAt(LocalDateTime.now());
        outboundOrderMapper.updateById(order);
        return R.ok(Map.of("order", outboundOrderMapper.selectById(id), "items", items));
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
