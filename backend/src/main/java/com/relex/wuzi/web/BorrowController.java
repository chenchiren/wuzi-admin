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
 * 借用申请 / 借出 / 归还 / 逾期查询
 * <p>依赖表 borrow_order / borrow_order_item / borrow_return_record 已建（见 sql/wuzi_full.sql）。</p>
 */
@RestController
@RequestMapping("/api/borrows")
@RequiredArgsConstructor
public class BorrowController {

    private final BorrowOrderMapper orderMapper;
    private final BorrowOrderItemMapper itemMapper;
    private final BorrowReturnRecordMapper returnRecordMapper;
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
        private String remark;
    }

    @Data
    public static class CreateReq {
        private String borrowType;
        private String projectName;
        private LocalDate expectReturnDate;
        private String purpose;
        private String remark;
        private List<CreateItem> items;
    }

    @Data
    public static class ApproveReq {
        private Boolean approved;
        private String comment;
    }

    @Data
    public static class LendReq {
        private Long warehouseId;
        private String remark;
    }

    @Data
    public static class ReturnItem {
        private Long itemId;
        private Long materialId;
        private BigDecimal returnQty;
    }

    @Data
    public static class ReturnReq {
        private Long warehouseId;
        private String conditionStatus;
        private String remark;
        private List<ReturnItem> items;
    }

    @GetMapping
    public R<PageResult<BorrowOrder>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<BorrowOrder> qw = new LambdaQueryWrapper<BorrowOrder>()
                .eq(status != null && !status.isBlank(), BorrowOrder::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(BorrowOrder::getBorrowNo, keyword)
                        .or().like(BorrowOrder::getProjectName, keyword)
                        .or().like(BorrowOrder::getPurpose, keyword))
                .orderByDesc(BorrowOrder::getId);
        Page<BorrowOrder> p = orderMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        BorrowOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("借用单不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("order", order);
        map.put("items", itemMapper.selectList(new LambdaQueryWrapper<BorrowOrderItem>()
                .eq(BorrowOrderItem::getBorrowId, id)));
        map.put("returns", returnRecordMapper.selectList(new LambdaQueryWrapper<BorrowReturnRecord>()
                .eq(BorrowReturnRecord::getBorrowId, id)
                .orderByDesc(BorrowReturnRecord::getId)));
        return R.ok(map);
    }

    @RequirePerm("asset:borrow:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("借用明细不能为空");
        }
        if (req.getExpectReturnDate() == null) {
            throw new BizException("请指定预计归还日期");
        }
        BorrowOrder order = new BorrowOrder();
        order.setBorrowNo(BillNoUtil.next("BO"));
        order.setApplyUserId(userId == null ? 0L : userId);
        order.setBorrowType(req.getBorrowType() == null ? "TEMP" : req.getBorrowType());
        order.setProjectName(req.getProjectName());
        order.setBorrowDate(LocalDate.now());
        order.setExpectReturnDate(req.getExpectReturnDate());
        order.setPurpose(req.getPurpose());
        order.setStatus("DRAFT");
        order.setRemark(req.getRemark());
        order.setIsDeleted(0);
        order.setCreatedBy(userId);
        orderMapper.insert(order);

        List<BorrowOrderItem> items = new ArrayList<>();
        for (CreateItem it : req.getItems()) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            if (it.getQty() == null || it.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("借用数量必须大于 0");
            }
            BorrowOrderItem item = new BorrowOrderItem();
            item.setBorrowId(order.getId());
            item.setMaterialId(it.getMaterialId());
            item.setQty(it.getQty());
            item.setLentQty(BigDecimal.ZERO);
            item.setReturnedQty(BigDecimal.ZERO);
            item.setUnit(it.getUnit() != null ? it.getUnit() : material.getUnit());
            item.setRemark(it.getRemark());
            itemMapper.insert(item);
            items.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", items);
        return R.ok(result);
    }

    /** 提交借用申请进入审批流 */
    @RequirePerm("asset:borrow:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<BorrowOrder> submit(@PathVariable Long id,
                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        BorrowOrder order = requireOrder(id);
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BizException("当前状态不可提交: " + order.getStatus());
        }
        List<BorrowOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<BorrowOrderItem>()
                .eq(BorrowOrderItem::getBorrowId, id));
        if (items.isEmpty()) {
            throw new BizException("借用明细不能为空");
        }
        flowApprovalService.start(ApprovalService.BIZ_BORROW, ApprovalService.BIZ_BORROW,
                id, order.getBorrowNo(), userId == null ? 0L : userId,
                "借用申请-" + order.getBorrowNo(),
                status -> {
                    order.setStatus(status);
                    orderMapper.updateById(order);
                });
        return R.ok(order);
    }

    @RequirePerm("asset:borrow:approve")
    @PostMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public R<BorrowOrder> approve(@PathVariable Long id,
                                  @RequestBody ApproveReq req,
                                  @RequestAttribute(value = "userId", required = false) Long userId) {
        BorrowOrder order = requireOrder(id);
        boolean approved = req.getApproved() == null || req.getApproved();
        ApprovalInstance inst = flowApprovalService.getByBizIfPresent(ApprovalService.BIZ_BORROW, id);
        if (inst != null && flowApprovalService.hasFlow(inst.getId())
                && ApprovalService.INST_PENDING.equals(inst.getStatus())) {
            if (approved) {
                flowApprovalService.approve(inst.getId(), userId, req.getComment(), null);
            } else {
                flowApprovalService.reject(inst.getId(), userId, req.getComment(), null);
            }
            return R.ok(orderMapper.selectById(id));
        }
        if (!"SUBMITTED".equals(order.getStatus())
                && !ApprovalService.ST_PENDING.equals(order.getStatus())) {
            throw new BizException("请先提交审批，当前状态不可直接通过: " + order.getStatus());
        }
        order.setStatus(approved ? "APPROVED" : "REJECTED");
        order.setRemark(req.getComment());
        order.setUpdatedBy(userId);
        orderMapper.updateById(order);
        return R.ok(order);
    }

    /**
     * 借出：扣减库存，写 stock_record（biz_type=BORROW_LEND）
     */
    @RequirePerm("asset:borrow:lend")
    @PostMapping("/{id}/lend")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> lend(@PathVariable Long id,
                                       @RequestBody LendReq req,
                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        BorrowOrder order = requireOrder(id);
        if (!"APPROVED".equals(order.getStatus())) {
            throw new BizException("仅已审批的借用单可借出，当前: " + order.getStatus());
        }
        if (req.getWarehouseId() == null) {
            throw new BizException("请指定仓库");
        }
        List<BorrowOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<BorrowOrderItem>()
                .eq(BorrowOrderItem::getBorrowId, id));
        List<Map<String, Object>> records = new ArrayList<>();
        for (BorrowOrderItem item : items) {
            BigDecimal lendQty = item.getQty();
            changeStock("BORROW_LEND", order.getBorrowNo(), order.getId(),
                    req.getWarehouseId(), item.getMaterialId(), lendQty.negate(), userId, req.getRemark());
            item.setLentQty(lendQty);
            itemMapper.updateById(item);
        }
        order.setStatus("LENT");
        order.setUpdatedBy(userId);
        orderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", items);
        return R.ok(result);
    }

    /**
     * 归还：增加库存，写 borrow_return_record + stock_record（biz_type=BORROW_RETURN）
     */
    @RequirePerm("asset:borrow:return")
    @PostMapping("/{id}/return")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> doReturn(@PathVariable Long id,
                                           @RequestBody ReturnReq req,
                                           @RequestAttribute(value = "userId", required = false) Long userId) {
        BorrowOrder order = requireOrder(id);
        if (!"LENT".equals(order.getStatus()) && !"PART_RETURNED".equals(order.getStatus()) && !"OVERDUE".equals(order.getStatus())) {
            throw new BizException("当前状态不可归还: " + order.getStatus());
        }
        if (req.getWarehouseId() == null) {
            throw new BizException("请指定仓库");
        }
        List<BorrowOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<BorrowOrderItem>()
                .eq(BorrowOrderItem::getBorrowId, id));
        Map<Long, BorrowOrderItem> byId = new HashMap<>();
        Map<Long, BorrowOrderItem> byMaterial = new HashMap<>();
        for (BorrowOrderItem it : items) {
            byId.put(it.getId(), it);
            byMaterial.put(it.getMaterialId(), it);
        }

        List<ReturnItem> returnItems = req.getItems();
        if (returnItems == null || returnItems.isEmpty()) {
            returnItems = new ArrayList<>();
            for (BorrowOrderItem it : items) {
                BigDecimal remain = nvl(it.getLentQty()).subtract(nvl(it.getReturnedQty()));
                if (remain.compareTo(BigDecimal.ZERO) > 0) {
                    ReturnItem ri = new ReturnItem();
                    ri.setItemId(it.getId());
                    ri.setMaterialId(it.getMaterialId());
                    ri.setReturnQty(remain);
                    returnItems.add(ri);
                }
            }
        }

        List<BorrowReturnRecord> records = new ArrayList<>();
        for (ReturnItem ri : returnItems) {
            if (ri.getReturnQty() == null || ri.getReturnQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("归还数量必须大于 0");
            }
            BorrowOrderItem item = ri.getItemId() != null ? byId.get(ri.getItemId()) : byMaterial.get(ri.getMaterialId());
            if (item == null) {
                throw new BizException("借用明细不存在");
            }
            BigDecimal remain = nvl(item.getLentQty()).subtract(nvl(item.getReturnedQty()));
            if (ri.getReturnQty().compareTo(remain) > 0) {
                throw new BizException("归还数量超出未还数量: " + remain);
            }
            item.setReturnedQty(nvl(item.getReturnedQty()).add(ri.getReturnQty()));
            itemMapper.updateById(item);

            BorrowReturnRecord rec = new BorrowReturnRecord();
            rec.setBorrowId(order.getId());
            rec.setBorrowItemId(item.getId());
            rec.setMaterialId(item.getMaterialId());
            rec.setReturnQty(ri.getReturnQty());
            rec.setConditionStatus(req.getConditionStatus() == null ? "GOOD" : req.getConditionStatus());
            rec.setOperatorId(userId);
            rec.setRemark(req.getRemark());
            rec.setReturnTime(LocalDateTime.now());
            rec.setCreatedAt(LocalDateTime.now());
            returnRecordMapper.insert(rec);
            records.add(rec);

            changeStock("BORROW_RETURN", order.getBorrowNo(), order.getId(),
                    req.getWarehouseId(), item.getMaterialId(), ri.getReturnQty(), userId, req.getRemark());
        }

        items = itemMapper.selectList(new LambdaQueryWrapper<BorrowOrderItem>()
                .eq(BorrowOrderItem::getBorrowId, id));
        boolean allReturned = items.stream().allMatch(it ->
                nvl(it.getReturnedQty()).compareTo(nvl(it.getLentQty())) >= 0);

        order.setStatus(allReturned ? "RETURNED" : "PART_RETURNED");
        if (allReturned) {
            order.setActualReturnDate(LocalDate.now());
        }
        order.setUpdatedBy(userId);
        orderMapper.updateById(order);

        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("returns", records);
        return R.ok(result);
    }

    /**
     * 逾期未还列表：expect_return_date < today 且仍有未归还数量
     * 批量加载明细，避免按单循环查库
     */
    @GetMapping("/overdue")
    public R<List<Map<String, Object>>> overdue() {
        LocalDate today = LocalDate.now();
        List<BorrowOrder> candidates = orderMapper.selectList(new LambdaQueryWrapper<BorrowOrder>()
                .in(BorrowOrder::getStatus, "LENT", "PART_RETURNED", "OVERDUE")
                .isNotNull(BorrowOrder::getExpectReturnDate)
                .lt(BorrowOrder::getExpectReturnDate, today)
                .orderByAsc(BorrowOrder::getExpectReturnDate));
        if (candidates.isEmpty()) {
            return R.ok(List.of());
        }
        List<Long> ids = candidates.stream().map(BorrowOrder::getId).toList();
        List<BorrowOrderItem> allItems = itemMapper.selectList(new LambdaQueryWrapper<BorrowOrderItem>()
                .in(BorrowOrderItem::getBorrowId, ids));
        Map<Long, BigDecimal> unreturnedByBorrow = new HashMap<>();
        for (BorrowOrderItem it : allItems) {
            BigDecimal remain = nvl(it.getLentQty()).subtract(nvl(it.getReturnedQty()));
            unreturnedByBorrow.merge(it.getBorrowId(), remain, BigDecimal::add);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (BorrowOrder order : candidates) {
            BigDecimal unreturned = unreturnedByBorrow.getOrDefault(order.getId(), BigDecimal.ZERO);
            if (unreturned.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("order", order);
            row.put("unreturnedQty", unreturned);
            row.put("overdueDays", java.time.temporal.ChronoUnit.DAYS.between(order.getExpectReturnDate(), today));
            result.add(row);
        }
        return R.ok(result);
    }

    @GetMapping("/{id}/return-records")
    public R<List<BorrowReturnRecord>> returnRecords(@PathVariable Long id) {
        return R.ok(returnRecordMapper.selectList(new LambdaQueryWrapper<BorrowReturnRecord>()
                .eq(BorrowReturnRecord::getBorrowId, id)
                .orderByDesc(BorrowReturnRecord::getId)));
    }

    private BorrowOrder requireOrder(Long id) {
        BorrowOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("借用单不存在");
        }
        return order;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
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
