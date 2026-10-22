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
 * 报废申请 / 确认
 * <p>依赖表 scrap_order / scrap_order_item 已建（见 sql/wuzi_full.sql）。</p>
 * status: DRAFT -> SUBMITTED -> CONFIRMED (或 REJECTED)
 */
@RestController
@RequestMapping("/api/scraps")
@RequiredArgsConstructor
public class ScrapController {

    private final ScrapOrderMapper orderMapper;
    private final ScrapOrderItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final StockMapper stockMapper;
    private final StockRecordMapper stockRecordMapper;
    private final FlowApprovalService flowApprovalService;
    private final StockService stockService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class CreateItem {
        private Long materialId;
        private BigDecimal qty;
        private String unit;
        private String reason;
        private String remark;
    }

    @Data
    public static class CreateReq {
        private String scrapReason;
        private LocalDate scrapDate;
        private String remark;
        private List<CreateItem> items;
    }

    @Data
    public static class ConfirmReq {
        private Long warehouseId;
        private String comment;
    }

    @GetMapping
    public R<PageResult<ScrapOrder>> page(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<ScrapOrder> qw = new LambdaQueryWrapper<ScrapOrder>()
                .eq(status != null && !status.isBlank(), ScrapOrder::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(ScrapOrder::getScrapNo, keyword)
                        .or().like(ScrapOrder::getScrapReason, keyword)
                        .or().like(ScrapOrder::getRemark, keyword))
                .orderByDesc(ScrapOrder::getId);
        Page<ScrapOrder> p = orderMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        ScrapOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("报废单不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("order", order);
        map.put("items", itemMapper.selectList(new LambdaQueryWrapper<ScrapOrderItem>()
                .eq(ScrapOrderItem::getScrapId, id)));
        return R.ok(map);
    }

    @RequirePerm("asset:scrap:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("报废明细不能为空");
        }
        ScrapOrder order = new ScrapOrder();
        order.setScrapNo(BillNoUtil.next("SP"));
        order.setApplyUserId(userId == null ? 0L : userId);
        order.setScrapReason(req.getScrapReason());
        order.setScrapDate(req.getScrapDate() == null ? LocalDate.now() : req.getScrapDate());
        order.setStatus("DRAFT");
        order.setRemark(req.getRemark());
        order.setIsDeleted(0);
        order.setCreatedBy(userId);
        orderMapper.insert(order);

        List<ScrapOrderItem> items = new ArrayList<>();
        for (CreateItem it : req.getItems()) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            if (it.getQty() == null || it.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("报废数量必须大于 0");
            }
            ScrapOrderItem item = new ScrapOrderItem();
            item.setScrapId(order.getId());
            item.setMaterialId(it.getMaterialId());
            item.setQty(it.getQty());
            item.setConfirmedQty(BigDecimal.ZERO);
            item.setUnit(it.getUnit() != null ? it.getUnit() : material.getUnit());
            item.setReason(it.getReason());
            item.setRemark(it.getRemark());
            itemMapper.insert(item);
            items.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", items);
        return R.ok(result);
    }

    @RequirePerm("asset:scrap:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<ScrapOrder> submit(@PathVariable Long id,
                                @RequestAttribute(value = "userId", required = false) Long userId) {
        ScrapOrder order = requireOrder(id);
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BizException("当前状态不可提交: " + order.getStatus());
        }
        List<ScrapOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<ScrapOrderItem>()
                .eq(ScrapOrderItem::getScrapId, id));
        if (items.isEmpty()) {
            throw new BizException("报废明细不能为空");
        }
        flowApprovalService.start(ApprovalService.BIZ_SCRAP, ApprovalService.BIZ_SCRAP,
                id, order.getScrapNo(), userId == null ? 0L : userId,
                "报废申请-" + order.getScrapNo(),
                status -> {
                    order.setStatus(status);
                    orderMapper.updateById(order);
                });
        return R.ok(order);
    }

    /**
     * 报废确认：扣减库存，写 stock_record（biz_type=SCRAP）
     * 仅审批通过（APPROVED）或兼容旧 SUBMITTED 状态可确认
     */
    @RequirePerm("asset:scrap:confirm")
    @PostMapping("/{id}/confirm")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> confirm(@PathVariable Long id,
                                          @RequestBody ConfirmReq req,
                                          @RequestAttribute(value = "userId", required = false) Long userId) {
        ScrapOrder order = requireOrder(id);
        if (!"APPROVED".equals(order.getStatus()) && !"SUBMITTED".equals(order.getStatus())) {
            throw new BizException("仅已审批通过的报废单可确认，当前: " + order.getStatus());
        }
        if (req.getWarehouseId() == null) {
            throw new BizException("请指定仓库");
        }
        List<ScrapOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<ScrapOrderItem>()
                .eq(ScrapOrderItem::getScrapId, id));
        List<Map<String, Object>> records = new ArrayList<>();
        for (ScrapOrderItem item : items) {
            BigDecimal qty = item.getQty();
            changeStock("SCRAP", order.getScrapNo(), order.getId(),
                    req.getWarehouseId(), item.getMaterialId(), qty.negate(), userId, req.getComment());
            item.setConfirmedQty(qty);
            itemMapper.updateById(item);
        }
        order.setStatus("CONFIRMED");
        order.setConfirmComment(req.getComment());
        order.setUpdatedBy(userId);
        orderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", items);
        result.put("records", records);
        return R.ok(result);
    }

    @RequirePerm("asset:scrap:reject")
    @PostMapping("/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    public R<ScrapOrder> reject(@PathVariable Long id,
                                @RequestBody ConfirmReq req,
                                @RequestAttribute(value = "userId", required = false) Long userId) {
        ScrapOrder order = requireOrder(id);
        ApprovalInstance inst = flowApprovalService.getByBizIfPresent(ApprovalService.BIZ_SCRAP, id);
        if (inst != null && flowApprovalService.hasFlow(inst.getId())
                && ApprovalService.INST_PENDING.equals(inst.getStatus())) {
            flowApprovalService.reject(inst.getId(), userId, req.getComment(), null);
            return R.ok(orderMapper.selectById(id));
        }
        if (!"SUBMITTED".equals(order.getStatus())
                && !ApprovalService.ST_PENDING.equals(order.getStatus())) {
            throw new BizException("仅已提交的报废单可驳回，当前: " + order.getStatus());
        }
        order.setStatus("REJECTED");
        order.setConfirmComment(req.getComment());
        order.setUpdatedBy(userId);
        orderMapper.updateById(order);
        return R.ok(order);
    }

    private ScrapOrder requireOrder(Long id) {
        ScrapOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("报废单不存在");
        }
        return order;
    }

    private void changeStock(String bizType, String bizNo, Long bizId,
                             Long warehouseId, Long materialId, BigDecimal changeQty,
                             Long operatorId, String remark) {
        StockService.StockChange cmd = new StockService.StockChange();
        cmd.setBizType(bizType);
        cmd.setBizNo(bizNo);
        cmd.setBizId(bizId);
        cmd.setWarehouseId(warehouseId);
        cmd.setMaterialId(materialId);
        cmd.setChangeQty(changeQty);
        cmd.setOperatorId(operatorId);
        cmd.setRemark(remark);
        stockService.changeAuto(cmd);
    }
}
