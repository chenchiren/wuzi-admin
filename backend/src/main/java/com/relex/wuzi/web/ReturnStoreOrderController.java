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
 * 退库单完整流程（领用退回）：
 *   申请(APPLIED) → 仓管验收(ACCEPTED) → 确认入库(CONFIRMED)
 * 确认入库时调用 StockService.increase，biz_type=USE_RETURN。
 * 可分支：REJECTED / CANCELLED / DRAFT。
 */
@RestController
@RequestMapping("/api/return-stores")
@RequiredArgsConstructor
public class ReturnStoreOrderController {

    public static final String ST_DRAFT = "DRAFT";
    public static final String ST_APPLIED = "APPLIED";
    public static final String ST_ACCEPTED = "ACCEPTED";
    public static final String ST_CONFIRMED = "CONFIRMED";
    public static final String ST_REJECTED = "REJECTED";
    public static final String ST_CANCELLED = "CANCELLED";
    public static final String BIZ_USE_RETURN = "USE_RETURN";

    private final ReturnStoreOrderMapper orderMapper;
    private final ReturnStoreOrderItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final MaterialUseApplyMapper applyMapper;
    private final StockService stockService;
    private final FlowApprovalService flowApprovalService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class CreateItem {
        private Long materialId;
        private String batchNo;
        private Long locationId;
        private BigDecimal qty;
        private BigDecimal taxInclPrice;
        private BigDecimal taxRate;
        private String conditionStatus;
        private String remark;
    }

    @Data
    public static class CreateReq {
        private Long applyId;
        private Long warehouseId;
        private String remark;
        private List<CreateItem> items;
    }

    @Data
    public static class AcceptItem {
        private Long itemId;
        private BigDecimal acceptQty;
        private String conditionStatus;
        private String remark;
    }

    @Data
    public static class AcceptReq {
        private String remark;
        private List<AcceptItem> items;
    }

    @Data
    public static class ConfirmReq {
        private String remark;
        /** 可选：按 itemId 覆盖实收数 */
        private List<AcceptItem> items;
    }

    @GetMapping
    public R<PageResult<ReturnStoreOrder>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long warehouseId) {
        LambdaQueryWrapper<ReturnStoreOrder> qw = new LambdaQueryWrapper<ReturnStoreOrder>()
                .eq(status != null && !status.isBlank(), ReturnStoreOrder::getStatus, status)
                .eq(warehouseId != null, ReturnStoreOrder::getWarehouseId, warehouseId)
                .orderByDesc(ReturnStoreOrder::getId);
        Page<ReturnStoreOrder> p = orderMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        ReturnStoreOrder order = require(id);
        List<ReturnStoreOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReturnStoreOrderItem>()
                .eq(ReturnStoreOrderItem::getReturnId, id));
        return R.ok(Map.of("order", order, "items", items));
    }

    /** 退库申请：创建退库单（DRAFT，提交审批后进入 PENDING/APPROVED） */
    @RequirePerm("asset:return:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        if (req.getWarehouseId() == null || warehouseMapper.selectById(req.getWarehouseId()) == null) {
            throw new BizException("仓库不存在");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("退库明细不能为空");
        }

        ReturnStoreOrder order = new ReturnStoreOrder();
        order.setReturnNo(BillNoUtil.next("RT"));
        order.setApplyId(req.getApplyId());
        order.setWarehouseId(req.getWarehouseId());
        order.setApplyUserId(userId);
        order.setStatus(ST_DRAFT);
        order.setApplyRemark(req.getRemark());
        order.setApplyTime(LocalDateTime.now());
        order.setIsDeleted(0);
        orderMapper.insert(order);

        List<ReturnStoreOrderItem> items = new ArrayList<>();
        for (CreateItem it : req.getItems()) {
            if (it.getMaterialId() == null) {
                throw new BizException("退库明细缺少物资ID");
            }
            if (it.getQty() == null || it.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("退库数量必须大于0");
            }
            if (materialMapper.selectById(it.getMaterialId()) == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            ReturnStoreOrderItem item = new ReturnStoreOrderItem();
            item.setReturnId(order.getId());
            item.setMaterialId(it.getMaterialId());
            item.setBatchNo(StockService.normalizeBatch(it.getBatchNo()).isEmpty() ? null
                    : StockService.normalizeBatch(it.getBatchNo()));
            item.setLocationId(it.getLocationId());
            item.setApplyQty(it.getQty());
            item.setTaxInclPrice(it.getTaxInclPrice() != null ? it.getTaxInclPrice() : BigDecimal.ZERO);
            item.setTaxRate(it.getTaxRate());
            item.setConditionStatus(it.getConditionStatus() != null ? it.getConditionStatus() : "GOOD");
            item.setRemark(it.getRemark());
            itemMapper.insert(item);
            items.add(item);
        }
        return R.ok(Map.of("order", order, "items", items));
    }

    /** 提交退库进入审批流（通过后 APPROVED，供仓管验收；旧 APPLIED 数据仍可验收） */
    @RequirePerm("asset:return:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<ReturnStoreOrder> submit(@PathVariable Long id,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        ReturnStoreOrder order = require(id);
        if (!ST_DRAFT.equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BizException("当前状态不可提交: " + order.getStatus());
        }
        List<ReturnStoreOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReturnStoreOrderItem>()
                .eq(ReturnStoreOrderItem::getReturnId, id));
        if (items.isEmpty()) {
            throw new BizException("退库明细不能为空");
        }
        flowApprovalService.start(ApprovalService.BIZ_RETURN_STORE, ApprovalService.BIZ_RETURN_STORE,
                id, order.getReturnNo(), userId == null ? 0L : userId,
                "退库申请-" + order.getReturnNo(),
                status -> {
                    order.setStatus(status);
                    order.setUpdatedAt(LocalDateTime.now());
                    orderMapper.updateById(order);
                });
        return R.ok(order);
    }

    /** 仓管验收：仅审批通过（APPROVED）或旧数据 APPLIED 可验收 */
    @RequirePerm("asset:return:accept")
    @PostMapping("/{id}/accept")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> accept(@PathVariable Long id,
                                         @RequestBody(required = false) AcceptReq req,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        ReturnStoreOrder order = require(id);
        if (!ApprovalService.ST_APPROVED.equals(order.getStatus())
                && !ST_APPLIED.equals(order.getStatus())) {
            throw new BizException("仅审批通过的退库单可验收，当前: " + order.getStatus());
        }
        List<ReturnStoreOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReturnStoreOrderItem>()
                .eq(ReturnStoreOrderItem::getReturnId, id));
        if (items.isEmpty()) {
            throw new BizException("退库明细为空");
        }

        if (req != null && req.getItems() != null) {
            for (AcceptItem ai : req.getItems()) {
                ReturnStoreOrderItem target = items.stream()
                        .filter(x -> x.getId().equals(ai.getItemId()))
                        .findFirst().orElse(null);
                if (target == null) {
                    continue;
                }
                if (ai.getAcceptQty() != null) {
                    target.setAcceptQty(ai.getAcceptQty());
                }
                if (ai.getConditionStatus() != null) {
                    target.setConditionStatus(ai.getConditionStatus());
                }
                if (ai.getRemark() != null) {
                    target.setRemark(ai.getRemark());
                }
                itemMapper.updateById(target);
            }
            items = itemMapper.selectList(new LambdaQueryWrapper<ReturnStoreOrderItem>()
                    .eq(ReturnStoreOrderItem::getReturnId, id));
        }

        // 默认验收数 = 申请数
        for (ReturnStoreOrderItem item : items) {
            if (item.getAcceptQty() == null) {
                item.setAcceptQty(item.getApplyQty());
                itemMapper.updateById(item);
            }
            if (item.getAcceptQty().compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("验收数量不能为负");
            }
        }

        order.setAcceptUserId(userId);
        order.setAcceptRemark(req != null ? req.getRemark() : null);
        order.setAcceptTime(LocalDateTime.now());
        order.setStatus(ST_ACCEPTED);
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return R.ok(Map.of("order", orderMapper.selectById(id), "items", items));
    }

    /** 驳回退库：有审批实例走多级；旧 APPLIED 直接驳回 */
    @RequirePerm("asset:return:reject")
    @PostMapping("/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    public R<ReturnStoreOrder> reject(@PathVariable Long id,
                                      @RequestParam(required = false) String reason,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        ReturnStoreOrder order = require(id);
        ApprovalInstance inst = flowApprovalService.getByBizIfPresent(ApprovalService.BIZ_RETURN_STORE, id);
        if (inst != null && flowApprovalService.hasFlow(inst.getId())
                && ApprovalService.INST_PENDING.equals(inst.getStatus())) {
            flowApprovalService.reject(inst.getId(), userId, reason, null);
            return R.ok(orderMapper.selectById(id));
        }
        if (!ST_APPLIED.equals(order.getStatus())) {
            throw new BizException("仅申请/待审状态可驳回，当前: " + order.getStatus());
        }
        order.setStatus(ST_REJECTED);
        order.setAcceptUserId(userId);
        order.setAcceptRemark(reason);
        order.setAcceptTime(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return R.ok(order);
    }

    /** 确认入库：ACCEPTED → CONFIRMED，StockService.increase(biz_type=USE_RETURN) */
    @RequirePerm("asset:return:confirm")
    @PostMapping("/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> confirm(@PathVariable Long id,
                                          @RequestBody(required = false) ConfirmReq req,
                                          @RequestAttribute(value = "userId", required = false) Long userId) {
        ReturnStoreOrder order = require(id);
        if (!ST_ACCEPTED.equals(order.getStatus())) {
            throw new BizException("仅验收通过状态可确认入库，当前: " + order.getStatus());
        }
        List<ReturnStoreOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<ReturnStoreOrderItem>()
                .eq(ReturnStoreOrderItem::getReturnId, id));
        if (items.isEmpty()) {
            throw new BizException("退库明细为空");
        }

        if (req != null && req.getItems() != null) {
            for (AcceptItem ai : req.getItems()) {
                ReturnStoreOrderItem target = items.stream()
                        .filter(x -> x.getId().equals(ai.getItemId()))
                        .findFirst().orElse(null);
                if (target != null && ai.getAcceptQty() != null) {
                    target.setAcceptQty(ai.getAcceptQty());
                    itemMapper.updateById(target);
                }
            }
            items = itemMapper.selectList(new LambdaQueryWrapper<ReturnStoreOrderItem>()
                    .eq(ReturnStoreOrderItem::getReturnId, id));
        }

        List<Map<String, Object>> records = new ArrayList<>();
        for (ReturnStoreOrderItem item : items) {
            BigDecimal qty = item.getActualQty() != null && item.getActualQty().compareTo(BigDecimal.ZERO) > 0
                    ? item.getActualQty()
                    : (item.getAcceptQty() != null ? item.getAcceptQty() : item.getApplyQty());
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String batchNo = StockService.normalizeBatch(item.getBatchNo());
            BigDecimal price = item.getTaxInclPrice() != null ? item.getTaxInclPrice() : BigDecimal.ZERO;
            BigDecimal amount = price.multiply(qty).setScale(2, java.math.RoundingMode.HALF_UP);

            StockService.StockChange change = new StockService.StockChange();
            change.setBizType(BIZ_USE_RETURN);
            change.setBizNo(order.getReturnNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getWarehouseId());
            change.setLocationId(item.getLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(batchNo);
            change.setChangeQty(qty);
            change.setTaxInclAmount(amount);
            change.setCostTaxIncl(amount);
            change.setTaxRate(item.getTaxRate());
            change.setTaxInclPrice(price);
            change.setOperatorId(userId);
            change.setRemark("退库确认入库");
            StockRecord rec = stockService.increase(change);
            records.add(Map.of("record", rec, "itemId", item.getId()));

            item.setActualQty(qty);
            itemMapper.updateById(item);
        }

        order.setStatus(ST_CONFIRMED);
        order.setConfirmUserId(userId);
        order.setConfirmRemark(req != null ? req.getRemark() : null);
        order.setConfirmTime(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 若关联领用申请，尝试关闭
        if (order.getApplyId() != null) {
            MaterialUseApply apply = applyMapper.selectById(order.getApplyId());
            if (apply != null && ("ISSUED".equals(apply.getStatus()) || "CLOSED".equals(apply.getStatus()))) {
                apply.setStatus("CLOSED");
                applyMapper.updateById(apply);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("order", orderMapper.selectById(id));
        result.put("items", items);
        result.put("records", records);
        return R.ok(result);
    }

    /** 作废（仅 DRAFT / APPLIED / REJECTED） */
    @RequirePerm("asset:return:cancel")
    @PostMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<ReturnStoreOrder> cancel(@PathVariable Long id) {
        ReturnStoreOrder order = require(id);
        if (!ST_DRAFT.equals(order.getStatus())
                && !ST_APPLIED.equals(order.getStatus())
                && !ST_REJECTED.equals(order.getStatus())) {
            throw new BizException("当前状态不可作废: " + order.getStatus());
        }
        order.setStatus(ST_CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return R.ok(order);
    }

    @RequirePerm("asset:return:delete")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> delete(@PathVariable Long id) {
        ReturnStoreOrder order = require(id);
        if (!ST_DRAFT.equals(order.getStatus())
                && !ST_APPLIED.equals(order.getStatus())
                && !ST_REJECTED.equals(order.getStatus())
                && !ST_CANCELLED.equals(order.getStatus())) {
            throw new BizException("仅草稿/申请/驳回/作废单可删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<ReturnStoreOrderItem>()
                .eq(ReturnStoreOrderItem::getReturnId, id));
        orderMapper.deleteById(id);
        return R.ok();
    }

    private ReturnStoreOrder require(Long id) {
        ReturnStoreOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("退库单不存在");
        }
        return order;
    }
}
