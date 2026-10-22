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
import com.relex.wuzi.service.PriceTaxCalculator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 采购到货：按订单到货、登记验收数量、提交
 */
@RestController
@RequestMapping("/api/purchase/arrivals")
@RequiredArgsConstructor
public class PurchaseArrivalController {

    private final PurchaseArrivalMapper arrivalMapper;
    private final PurchaseArrivalItemMapper arrivalItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final PriceTaxCalculator calculator;
    private final FlowApprovalService flowApprovalService;

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    @Data
    public static class CreateArriveLine {
        private Long purchaseOrderItemId;
        private BigDecimal arriveQty;
    }

    @Data
    public static class CreateReq {
        private Long orderId;
        private String transportInfo;
        private String remark;
        private List<CreateArriveLine> lines;
    }

    @Data
    public static class AcceptLine {
        private Long purchaseOrderItemId;
        /** 验收合格数量 */
        private BigDecimal qualifiedQty;
    }

    @Data
    public static class AcceptReq {
        private List<AcceptLine> lines;
    }

    @GetMapping
    public R<PageResult<PurchaseArrival>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Long orderId) {
        LambdaQueryWrapper<PurchaseArrival> qw = new LambdaQueryWrapper<PurchaseArrival>()
                .eq(status != null && !status.isBlank(), PurchaseArrival::getStatus, status)
                .eq(orderId != null, PurchaseArrival::getOrderId, orderId)
                .orderByDesc(PurchaseArrival::getId);
        Page<PurchaseArrival> p = arrivalMapper.selectPage(new Page<>(page, size), qw);
        fillOrderNo(p.getRecords());
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    private void fillOrderNo(List<PurchaseArrival> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> orderIds = list.stream().map(PurchaseArrival::getOrderId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> supplierIds = list.stream().map(PurchaseArrival::getSupplierId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> orderNos = orderIds.isEmpty() ? Map.of()
                : orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(PurchaseOrder::getId, PurchaseOrder::getOrderNo, (a, b) -> a));
        Map<Long, String> supplierNames = supplierIds.isEmpty() ? Map.of()
                : supplierMapper.selectBatchIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, Supplier::getSupplierName, (a, b) -> a));
        for (PurchaseArrival a : list) {
            if (a.getOrderId() != null) a.setOrderNo(orderNos.get(a.getOrderId()));
            if (a.getSupplierId() != null) a.setSupplierName(supplierNames.get(a.getSupplierId()));
        }
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        PurchaseArrival arrival = require(id);
        List<PurchaseArrivalItem> items = arrivalItemMapper.selectList(new LambdaQueryWrapper<PurchaseArrivalItem>()
                .eq(PurchaseArrivalItem::getArrivalId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("arrival", arrival);
        map.put("items", items);
        return R.ok(map);
    }

    /** 按采购订单生成到货单；未指定 lines 时带出全部未收货行 */
    @RequirePerm("po:arrival:create")
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req,
                                         @RequestAttribute("userId") Long userId) {
        if (req.getOrderId() == null) {
            throw new BizException("订单不能为空");
        }
        PurchaseOrder order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BizException("采购订单不存在");
        }
        if (ApprovalService.ST_CANCELLED.equals(order.getStatus())
                || ApprovalService.ST_REJECTED.equals(order.getStatus())
                || ApprovalService.ST_DRAFT.equals(order.getStatus())
                || ApprovalService.ST_PENDING.equals(order.getStatus())) {
            throw new BizException("订单需审批通过后才可到货，当前: " + order.getStatus());
        }

        List<PurchaseOrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, req.getOrderId()));
        Map<Long, PurchaseOrderItem> itemMap = new LinkedHashMap<>();
        orderItems.forEach(i -> itemMap.put(i.getId(), i));

        PurchaseArrival arrival = new PurchaseArrival();
        arrival.setArrivalNo(BillNoUtil.next("AR"));
        arrival.setOrderId(req.getOrderId());
        arrival.setSupplierId(order.getSupplierId());
        arrival.setArrivalTime(LocalDateTime.now());
        arrival.setOperatorId(userId);
        arrival.setTransportInfo(req.getTransportInfo());
        arrival.setStatus(ApprovalService.ST_DRAFT);
        arrival.setRemark(req.getRemark());
        arrivalMapper.insert(arrival);

        List<PurchaseArrivalItem> items = new ArrayList<>();
        if (req.getLines() != null && !req.getLines().isEmpty()) {
            for (CreateArriveLine line : req.getLines()) {
                PurchaseOrderItem oi = itemMap.get(line.getPurchaseOrderItemId());
                if (oi == null) {
                    throw new BizException("订单明细不存在: " + line.getPurchaseOrderItemId());
                }
                items.add(buildArrivalItem(arrival.getId(), oi, line.getArriveQty()));
            }
        } else {
            for (PurchaseOrderItem oi : orderItems) {
                BigDecimal remain = nz(oi.getQty()).subtract(nz(oi.getReceivedQty()));
                if (remain.compareTo(BigDecimal.ZERO) > 0) {
                    items.add(buildArrivalItem(arrival.getId(), oi, remain));
                }
            }
            if (items.isEmpty()) {
                throw new BizException("订单已全部到货，无待收货行");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("arrival", arrival);
        result.put("items", items);
        return R.ok(result);
    }

    /** 登记验收数量（合格数量），仅草稿可改 */
    @RequirePerm("po:arrival:accept")
    @PostMapping("/{id}/accept")
    @Transactional(rollbackFor = Exception.class)
    public R<List<PurchaseArrivalItem>> accept(@PathVariable Long id, @RequestBody AcceptReq req) {
        PurchaseArrival arrival = require(id);
        if (!ApprovalService.ST_DRAFT.equals(arrival.getStatus())) {
            throw new BizException("仅草稿状态可登记验收");
        }
        if (req.getLines() == null || req.getLines().isEmpty()) {
            throw new BizException("验收明细不能为空");
        }
        List<PurchaseArrivalItem> result = new ArrayList<>();
        for (AcceptLine line : req.getLines()) {
            if (line.getPurchaseOrderItemId() == null) {
                throw new BizException("订单明细ID不能为空");
            }
            PurchaseArrivalItem item = arrivalItemMapper.selectOne(new LambdaQueryWrapper<PurchaseArrivalItem>()
                    .eq(PurchaseArrivalItem::getArrivalId, id)
                    .eq(PurchaseArrivalItem::getPurchaseOrderItemId, line.getPurchaseOrderItemId())
                    .last("limit 1"));
            if (item == null) {
                throw new BizException("到货明细不存在");
            }
            if (line.getQualifiedQty() == null || line.getQualifiedQty().compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("验收数量非法");
            }
            if (line.getQualifiedQty().compareTo(nz(item.getArriveQty())) > 0) {
                throw new BizException("验收数量不能大于到货数量");
            }
            // 回写订单行合格数量
            PurchaseOrderItem oi = orderItemMapper.selectById(line.getPurchaseOrderItemId());
            if (oi != null) {
                oi.setQualifiedQty(line.getQualifiedQty());
                orderItemMapper.updateById(oi);
            }
            result.add(item);
        }
        return R.ok(result);
    }

    /** 提交到货进入审批流（不直接完成收货） */
    @RequirePerm("po:arrival:submit")
    @PostMapping("/{id}/submit")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseArrival> submit(@PathVariable Long id,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        PurchaseArrival arrival = require(id);
        if (!ApprovalService.ST_DRAFT.equals(arrival.getStatus())
                && !ApprovalService.ST_REJECTED.equals(arrival.getStatus())) {
            throw new BizException("仅草稿/驳回状态可提交，当前: " + arrival.getStatus());
        }
        List<PurchaseArrivalItem> items = arrivalItemMapper.selectList(new LambdaQueryWrapper<PurchaseArrivalItem>()
                .eq(PurchaseArrivalItem::getArrivalId, id));
        if (items.isEmpty()) {
            throw new BizException("到货明细不能为空");
        }
        flowApprovalService.start(ApprovalService.BIZ_ARRIVAL, ApprovalService.BIZ_ARRIVAL,
                id, arrival.getArrivalNo(), userId == null ? 0L : userId,
                "到货验收-" + arrival.getArrivalNo(),
                status -> {
                    arrival.setStatus(status);
                    arrivalMapper.updateById(arrival);
                });
        return R.ok(arrival);
    }

    /** 审批通过后确认到货：累计订单已收数量，到货单置为 COMPLETED */
    @RequirePerm("po:arrival:complete")
    @PostMapping("/{id}/complete")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseArrival> complete(@PathVariable Long id) {
        PurchaseArrival arrival = require(id);
        if (!ApprovalService.ST_APPROVED.equals(arrival.getStatus())) {
            throw new BizException("仅审批通过的到货单可确认完成，当前: " + arrival.getStatus());
        }
        List<PurchaseArrivalItem> items = arrivalItemMapper.selectList(new LambdaQueryWrapper<PurchaseArrivalItem>()
                .eq(PurchaseArrivalItem::getArrivalId, id));
        if (items.isEmpty()) {
            throw new BizException("到货明细不能为空");
        }
        for (PurchaseArrivalItem item : items) {
            PurchaseOrderItem oi = orderItemMapper.selectById(item.getPurchaseOrderItemId());
            if (oi == null) {
                continue;
            }
            BigDecimal arrive = nz(item.getArriveQty());
            oi.setReceivedQty(nz(oi.getReceivedQty()).add(arrive));
            if (oi.getQualifiedQty() == null) {
                oi.setQualifiedQty(arrive);
            }
            orderItemMapper.updateById(oi);
        }
        // 回写采购单状态，否则审批通过后卡在 APPROVED，入库接口会拒绝
        PurchaseArrival arr = arrival;
        List<PurchaseOrderItem> allItems = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, arr.getOrderId()));
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalReceived = BigDecimal.ZERO;
        for (PurchaseOrderItem oi : allItems) {
            totalQty = totalQty.add(nz(oi.getQty()));
            totalReceived = totalReceived.add(nz(oi.getReceivedQty()));
        }
        PurchaseOrder po = orderMapper.selectById(arr.getOrderId());
        if (po != null) {
            if (totalQty.compareTo(BigDecimal.ZERO) > 0 && totalReceived.compareTo(totalQty) >= 0) {
                po.setStatus("RECEIVED");
            } else if (totalReceived.compareTo(BigDecimal.ZERO) > 0) {
                po.setStatus("PART_RECEIVED");
            } else {
                po.setStatus("ARRIVED");
            }
            orderMapper.updateById(po);
        }
        arrival.setStatus(ApprovalService.ST_COMPLETED);
        arrivalMapper.updateById(arrival);
        return R.ok(arrival);
    }

    /** 作废草稿/驳回到货单 */
    @RequirePerm("po:arrival:cancel")
    @PostMapping("/{id}/cancel")
    @Transactional(rollbackFor = Exception.class)
    public R<PurchaseArrival> cancel(@PathVariable Long id) {
        PurchaseArrival arrival = require(id);
        if (!ApprovalService.ST_DRAFT.equals(arrival.getStatus())
                && !ApprovalService.ST_REJECTED.equals(arrival.getStatus())) {
            throw new BizException("仅草稿/驳回状态可作废");
        }
        arrival.setStatus(ApprovalService.ST_CANCELLED);
        arrivalMapper.updateById(arrival);
        return R.ok(arrival);
    }

    private PurchaseArrivalItem buildArrivalItem(Long arrivalId, PurchaseOrderItem oi, BigDecimal arriveQty) {
        if (arriveQty == null || arriveQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("到货数量必须大于0");
        }
        BigDecimal remain = nz(oi.getQty()).subtract(nz(oi.getReceivedQty()));
        if (arriveQty.compareTo(remain) > 0) {
            throw new BizException("到货数量超过订单未收数量");
        }
        BigDecimal price = oi.getTaxInclusivePrice();
        BigDecimal rate = oi.getTaxRate() != null ? oi.getTaxRate() : new BigDecimal("13");
        PriceTaxCalculator.Line line = calculator.fromInclusive(price, rate, arriveQty);

        PurchaseArrivalItem item = new PurchaseArrivalItem();
        item.setArrivalId(arrivalId);
        item.setPurchaseOrderItemId(oi.getId());
        item.setMaterialId(oi.getMaterialId());
        item.setTaxCode(oi.getTaxCode());
        item.setTaxRate(rate);
        item.setTaxInclusivePrice(line.taxInclusivePrice);
        item.setTaxExclusivePrice(line.taxExclusivePrice);
        item.setArriveQty(arriveQty);
        item.setTaxInclusiveAmount(line.taxInclusiveAmount);
        item.setTaxExclusiveAmount(line.taxExclAmount);
        item.setTaxAmount(line.taxAmount);
        arrivalItemMapper.insert(item);
        return item;
    }

    private PurchaseArrival require(Long id) {
        PurchaseArrival arrival = arrivalMapper.selectById(id);
        if (arrival == null) {
            throw new BizException("到货单不存在");
        }
        return arrival;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
