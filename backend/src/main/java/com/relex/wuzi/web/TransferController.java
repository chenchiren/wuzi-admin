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
 * 调拨出/入。依赖 transfer_order / transfer_order_item（表已建，见 sql/wuzi_full.sql）。
 */
@RestController
@RequestMapping("/api/transfer/orders")
@RequiredArgsConstructor
public class TransferController {

    private final TransferOrderMapper transferOrderMapper;
    private final TransferOrderItemMapper transferItemMapper;
    private final WarehouseMapper warehouseMapper;
    private final MaterialMapper materialMapper;
    private final StockService stockService;
    private final FlowApprovalService flowApprovalService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class CreateItem {
        private Long materialId;
        private String batchNo;
        private Long fromLocationId;
        private Long toLocationId;
        private BigDecimal qty;
    }

    @Data
    public static class CreateReq {
        private Long fromWarehouseId;
        private Long toWarehouseId;
        private String remark;
        private List<CreateItem> items;
    }

    @GetMapping
    public R<PageResult<TransferOrder>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String status) {
        LambdaQueryWrapper<TransferOrder> qw = new LambdaQueryWrapper<TransferOrder>()
                .eq(status != null && !status.isBlank(), TransferOrder::getStatus, status)
                .orderByDesc(TransferOrder::getId);
        Page<TransferOrder> p = transferOrderMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        TransferOrder order = transferOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("调拨单不存在");
        }
        List<TransferOrderItem> items = transferItemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferId, id));
        return R.ok(Map.of("order", order, "items", items));
    }

    @RequirePerm("wh:transfer:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req, @RequestAttribute("userId") Long userId) {
        if (req.getFromWarehouseId() == null || req.getToWarehouseId() == null) {
            throw new BizException("调出/调入仓库不能为空");
        }
        if (req.getFromWarehouseId().equals(req.getToWarehouseId())) {
            throw new BizException("调出与调入仓库不能相同");
        }
        if (warehouseMapper.selectById(req.getFromWarehouseId()) == null
                || warehouseMapper.selectById(req.getToWarehouseId()) == null) {
            throw new BizException("仓库不存在");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("调拨明细不能为空");
        }

        TransferOrder order = new TransferOrder();
        order.setTransferNo(BillNoUtil.next("TF"));
        order.setFromWarehouseId(req.getFromWarehouseId());
        order.setToWarehouseId(req.getToWarehouseId());
        order.setOperatorId(userId);
        order.setTransferTime(LocalDateTime.now());
        order.setStatus("DRAFT");
        order.setRemark(req.getRemark());
        order.setIsDeleted(0);
        transferOrderMapper.insert(order);

        List<TransferOrderItem> items = new ArrayList<>();
        for (CreateItem it : req.getItems()) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            if (it.getQty() == null || it.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("调拨数量必须大于0");
            }
            TransferOrderItem item = new TransferOrderItem();
            item.setTransferId(order.getId());
            item.setMaterialId(it.getMaterialId());
            item.setBatchNo(it.getBatchNo());
            item.setFromLocationId(it.getFromLocationId());
            item.setToLocationId(it.getToLocationId());
            item.setQty(it.getQty());
            item.setActualQty(BigDecimal.ZERO);
            transferItemMapper.insert(item);
            items.add(item);
        }
        return R.ok(Map.of("order", order, "items", items));
    }

    /** 提交调拨进入审批流 */
    @RequirePerm("wh:transfer:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<TransferOrder> submit(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        TransferOrder order = transferOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("调拨单不存在");
        }
        if (!"DRAFT".equals(order.getStatus()) && !"REJECTED".equals(order.getStatus())) {
            throw new BizException("当前状态不可提交: " + order.getStatus());
        }
        List<TransferOrderItem> items = transferItemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferId, id));
        if (items.isEmpty()) {
            throw new BizException("调拨明细为空");
        }
        flowApprovalService.start(ApprovalService.BIZ_TRANSFER, ApprovalService.BIZ_TRANSFER,
                id, order.getTransferNo(), userId, "调拨申请-" + order.getTransferNo(),
                status -> {
                    order.setStatus(status);
                    order.setUpdatedAt(LocalDateTime.now());
                    transferOrderMapper.updateById(order);
                });
        return R.ok(order);
    }

    /** 调拨出：从调出仓扣减（amount_sign=-1），需审批通过 */
    @RequirePerm("wh:transfer:out")
    @PostMapping("/{id}/out")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> outbound(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        TransferOrder order = transferOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("调拨单不存在");
        }
        if (!"APPROVED".equals(order.getStatus())) {
            throw new BizException("仅审批通过的调拨单可调出，当前: " + order.getStatus());
        }
        List<TransferOrderItem> items = transferItemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferId, id));
        if (items.isEmpty()) {
            throw new BizException("调拨明细为空");
        }
        for (TransferOrderItem item : items) {
            String batchNo = StockService.normalizeBatch(item.getBatchNo());
            BigDecimal available = stockService.availableQty(order.getFromWarehouseId(), item.getMaterialId(), batchNo);
            if (available.compareTo(item.getQty()) < 0) {
                throw new BizException("调出仓可用库存不足: 物料" + item.getMaterialId()
                        + " 可用" + available + "，需" + item.getQty());
            }
            StockService.StockChange change = new StockService.StockChange();
            change.setBizType("TRANSFER_OUT");
            change.setBizNo(order.getTransferNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getFromWarehouseId());
            change.setLocationId(item.getFromLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(batchNo);
            change.setChangeQty(item.getQty());
            change.setOperatorId(userId);
            change.setRemark("调拨出库");
            stockService.decrease(change);
        }
        order.setStatus("OUT_DONE");
        order.setUpdatedAt(LocalDateTime.now());
        transferOrderMapper.updateById(order);
        return R.ok(Map.of("order", transferOrderMapper.selectById(id), "items", items));
    }

    /** 调拨入：进入调入仓（amount_sign=1） */
    @RequirePerm("wh:transfer:in")
    @PostMapping("/{id}/in")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> inbound(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        TransferOrder order = transferOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("调拨单不存在");
        }
        if (!"OUT_DONE".equals(order.getStatus())) {
            throw new BizException("需先调拨出库，当前: " + order.getStatus());
        }
        List<TransferOrderItem> items = transferItemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferId, id));
        for (TransferOrderItem item : items) {
            String batchNo = StockService.normalizeBatch(item.getBatchNo());
            StockService.StockChange change = new StockService.StockChange();
            change.setBizType("TRANSFER_IN");
            change.setBizNo(order.getTransferNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getToWarehouseId());
            change.setLocationId(item.getToLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(batchNo);
            change.setChangeQty(item.getQty());
            change.setOperatorId(userId);
            change.setRemark("调拨入库");
            stockService.increase(change);
            item.setActualQty(item.getQty());
            transferItemMapper.updateById(item);
        }
        order.setStatus("CONFIRMED");
        order.setUpdatedAt(LocalDateTime.now());
        transferOrderMapper.updateById(order);
        return R.ok(Map.of("order", transferOrderMapper.selectById(id), "items", items));
    }
}
