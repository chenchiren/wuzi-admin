package com.relex.wuzi.web;

import com.relex.wuzi.common.BillNoUtil;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.annotation.DataScope;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.aspect.DataScopeSupport;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.Invoice;
import com.relex.wuzi.domain.PurchaseOrder;
import com.relex.wuzi.domain.PurchaseOrderItem;
import com.relex.wuzi.domain.SettleOrder;
import com.relex.wuzi.domain.SettleOrderItem;
import com.relex.wuzi.mapper.InvoiceMapper;
import com.relex.wuzi.mapper.PurchaseOrderItemMapper;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.SettleOrderItemMapper;
import com.relex.wuzi.mapper.SettleOrderMapper;
import com.relex.wuzi.service.SysUserDeptResolver;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 发票结算：按采购订单行核销，生成 settle_order / settle_order_item。
 */
@RestController
@RequestMapping("/api/settles")
@RequiredArgsConstructor
public class SettleController {

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    private final SettleOrderMapper settleMapper;
    private final SettleOrderItemMapper settleItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final InvoiceMapper invoiceMapper;
    private final SysUserDeptResolver deptResolver;

    @Data
    public static class SettleLine {
        private Long orderItemId;
        private BigDecimal settleQty;
        private BigDecimal settleAmount;
        private BigDecimal taxAmount;
        private String remark;
    }

    @Data
    public static class CreateReq {
        private Long orderId;
        private Long invoiceId;
        private LocalDate settleDate;
        private String remark;
        private List<SettleLine> items;
    }

    @GetMapping
    @DataScope(deptField = "dept_id", userField = "created_by")
    public R<PageResult<SettleOrder>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Long orderId,
                                           @RequestParam(required = false) Long invoiceId) {
        LambdaQueryWrapper<SettleOrder> qw = new LambdaQueryWrapper<SettleOrder>()
                .eq(status != null && !status.isBlank(), SettleOrder::getStatus, status)
                .eq(orderId != null, SettleOrder::getOrderId, orderId)
                .eq(invoiceId != null, SettleOrder::getInvoiceId, invoiceId)
                .orderByDesc(SettleOrder::getId);
        DataScopeSupport.apply(qw);
        Page<SettleOrder> p = settleMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        SettleOrder settle = settleMapper.selectById(id);
        if (settle == null || (settle.getIsDeleted() != null && settle.getIsDeleted() == 1)) {
            throw new BizException("结算单不存在");
        }
        List<SettleOrderItem> items = settleItemMapper.selectList(new LambdaQueryWrapper<SettleOrderItem>()
                .eq(SettleOrderItem::getSettleId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("settle", settle);
        map.put("items", items);
        map.put("order", settle.getOrderId() == null ? null : orderMapper.selectById(settle.getOrderId()));
        map.put("invoice", settle.getInvoiceId() == null ? null : invoiceMapper.selectById(settle.getInvoiceId()));
        return R.ok(map);
    }

    /** 订单行待核销余额 */
    @GetMapping("/order-items/{orderItemId}/balance")
    public R<Map<String, Object>> balance(@PathVariable Long orderItemId) {
        PurchaseOrderItem item = orderItemMapper.selectById(orderItemId);
        if (item == null) {
            throw new BizException("订单行不存在");
        }
        BigDecimal lineAmount = nvl(item.getTaxInclusiveAmount());
        BigDecimal settled = settledAmount(orderItemId);
        BigDecimal remain = lineAmount.subtract(settled);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("orderItemId", orderItemId);
        map.put("orderId", item.getOrderId());
        map.put("materialId", item.getMaterialId());
        map.put("lineAmount", lineAmount.setScale(2, RoundingMode.HALF_UP));
        map.put("settledAmount", settled.setScale(2, RoundingMode.HALF_UP));
        map.put("remainAmount", remain.setScale(2, RoundingMode.HALF_UP));
        return R.ok(map);
    }

    /** 按订单行核销生成结算单 */
    @PostMapping
    @RequirePerm("fin:settle:create")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> create(@RequestBody CreateReq req, @RequestAttribute("userId") Long userId) {
        if (req.getOrderId() == null) {
            throw new BizException("orderId 不能为空");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("核销明细不能为空");
        }
        PurchaseOrder order = orderMapper.selectById(req.getOrderId());
        if (order == null || (order.getIsDeleted() != null && order.getIsDeleted() == 1)) {
            throw new BizException("采购订单不存在");
        }
        if (req.getInvoiceId() != null) {
            Invoice invoice = invoiceMapper.selectById(req.getInvoiceId());
            if (invoice == null) {
                throw new BizException("发票不存在");
            }
            if (invoice.getOrderId() != null && !invoice.getOrderId().equals(order.getId())) {
                throw new BizException("发票与订单不匹配");
            }
        }

        SettleOrder settle = new SettleOrder();
        settle.setSettleNo(BillNoUtil.next("ST"));
        settle.setOrderId(order.getId());
        settle.setInvoiceId(req.getInvoiceId());
        settle.setSupplierId(order.getSupplierId());
        settle.setSettleDate(req.getSettleDate() != null ? req.getSettleDate() : LocalDate.now());
        settle.setStatus("CONFIRMED");
        settle.setRemark(req.getRemark());
        settle.setDeptId(deptResolver.resolveDeptId(userId, order.getDeptId()));
        settle.setIsDeleted(0);
        settle.setCreatedBy(userId);

        BigDecimal sumAmount = BigDecimal.ZERO;
        BigDecimal sumTax = BigDecimal.ZERO;
        List<SettleOrderItem> lines = new ArrayList<>();
        List<Map<String, Object>> lineResults = new ArrayList<>();

        for (SettleLine line : req.getItems()) {
            if (line.getOrderItemId() == null) {
                throw new BizException("orderItemId 不能为空");
            }
            PurchaseOrderItem orderItem = orderItemMapper.selectById(line.getOrderItemId());
            if (orderItem == null || !orderItem.getOrderId().equals(order.getId())) {
                throw new BizException("订单行不属于该订单: " + line.getOrderItemId());
            }
            BigDecimal amount = line.getSettleAmount();
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("核销金额必须大于0");
            }
            BigDecimal lineAmount = nvl(orderItem.getTaxInclusiveAmount());
            BigDecimal settled = settledAmount(orderItem.getId());
            BigDecimal remain = lineAmount.subtract(settled);
            if (amount.compareTo(remain) > 0) {
                throw new BizException("订单行 " + orderItem.getId() + " 核销超余额，剩余 "
                        + remain.setScale(2, RoundingMode.HALF_UP));
            }

            BigDecimal tax = line.getTaxAmount() != null ? line.getTaxAmount() : nvl(orderItem.getTaxAmount());
            SettleOrderItem item = new SettleOrderItem();
            item.setOrderItemId(orderItem.getId());
            item.setMaterialId(orderItem.getMaterialId());
            item.setSettleQty(line.getSettleQty() != null ? line.getSettleQty() : orderItem.getQty());
            item.setSettleAmount(amount.setScale(2, RoundingMode.HALF_UP));
            item.setTaxInclusiveAmount(amount.setScale(2, RoundingMode.HALF_UP));
            item.setTaxAmount(tax.setScale(2, RoundingMode.HALF_UP));
            item.setRemark(line.getRemark());
            lines.add(item);
            sumAmount = sumAmount.add(amount);
            sumTax = sumTax.add(tax);

            Map<String, Object> lr = new LinkedHashMap<>();
            lr.put("orderItemId", orderItem.getId());
            lr.put("lineAmount", lineAmount.setScale(2, RoundingMode.HALF_UP));
            lr.put("settledBefore", settled.setScale(2, RoundingMode.HALF_UP));
            lr.put("settleAmount", amount.setScale(2, RoundingMode.HALF_UP));
            lr.put("remainAfter", remain.subtract(amount).setScale(2, RoundingMode.HALF_UP));
            lineResults.add(lr);
        }

        settle.setSettleAmount(sumAmount.setScale(2, RoundingMode.HALF_UP));
        settle.setTaxAmount(sumTax.setScale(2, RoundingMode.HALF_UP));
        settleMapper.insert(settle);
        for (SettleOrderItem item : lines) {
            item.setSettleId(settle.getId());
            settleItemMapper.insert(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settle", settle);
        result.put("items", lines);
        result.put("lineBalances", lineResults);
        return R.ok(result);
    }

    private BigDecimal settledAmount(Long orderItemId) {
        List<SettleOrderItem> settled = settleItemMapper.selectList(new LambdaQueryWrapper<SettleOrderItem>()
                .eq(SettleOrderItem::getOrderItemId, orderItemId));
        return settled.stream()
                .map(i -> nvl(i.getSettleAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
