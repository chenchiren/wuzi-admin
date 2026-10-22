package com.relex.wuzi.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.annotation.DataScope;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.aspect.DataScopeSupport;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.domain.InboundOrderItem;
import com.relex.wuzi.domain.Invoice;
import com.relex.wuzi.domain.PurchaseOrder;
import com.relex.wuzi.domain.PurchaseOrderItem;
import com.relex.wuzi.mapper.InboundOrderItemMapper;
import com.relex.wuzi.mapper.InvoiceMapper;
import com.relex.wuzi.mapper.PurchaseOrderItemMapper;
import com.relex.wuzi.mapper.PurchaseOrderMapper;
import com.relex.wuzi.mapper.SupplierMapper;
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
 * 发票登记 + 三单匹配（订单含税 / 入库金额 / 发票金额差异分析）。
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal RATE_TOLERANCE = new BigDecimal("1.00");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 1000);

    private final InvoiceMapper invoiceMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final InboundOrderItemMapper inboundItemMapper;
    private final SupplierMapper supplierMapper;
    private final SysUserDeptResolver deptResolver;

    @Data
    public static class RegisterReq {
        private String invoiceNo;
        private String invoiceCode;
        private String invoiceType;
        private Long orderId;
        private LocalDate invoiceDate;
        private BigDecimal taxRate;
        private String taxCode;
        private BigDecimal taxInclusiveAmount;
        private BigDecimal taxAmount;
        private String remark;
    }

    @GetMapping
    @DataScope(deptField = "dept_id", userField = "created_by")
    public R<PageResult<Invoice>> page(@RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String matchStatus,
                                       @RequestParam(required = false) Long orderId,
                                       @RequestParam(required = false) Long supplierId) {
        LambdaQueryWrapper<Invoice> qw = new LambdaQueryWrapper<Invoice>()
                .eq(status != null && !status.isBlank(), Invoice::getStatus, status)
                .eq(matchStatus != null && !matchStatus.isBlank(), Invoice::getMatchStatus, matchStatus)
                .eq(orderId != null, Invoice::getOrderId, orderId)
                .eq(supplierId != null, Invoice::getSupplierId, supplierId)
                .orderByDesc(Invoice::getId);
        DataScopeSupport.apply(qw);
        Page<Invoice> p = invoiceMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null || (invoice.getIsDeleted() != null && invoice.getIsDeleted() == 1)) {
            throw new BizException("发票不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("invoice", invoice);
        if (invoice.getOrderId() != null) {
            map.put("order", orderMapper.selectById(invoice.getOrderId()));
            map.put("orderItems", orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                    .eq(PurchaseOrderItem::getOrderId, invoice.getOrderId())));
        }
        // 解析已保存的三单匹配结果，供前端「结果」按钮展示
        map.put("match", parseMatchResult(invoice));
        return R.ok(map);
    }

    private Map<String, Object> parseMatchResult(Invoice invoice) {
        String raw = invoice.getMatchResult();
        if (raw != null && !raw.isBlank()) {
            try {
                Map<?, ?> parsed = MAPPER.readValue(raw, Map.class);
                if (parsed != null && !parsed.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) parsed;
                    // 兼容旧数据只有 matchStatus 的情况
                    m.putIfAbsent("invoiceId", invoice.getId());
                    m.putIfAbsent("invoiceNo", invoice.getInvoiceNo());
                    m.putIfAbsent("orderId", invoice.getOrderId());
                    m.putIfAbsent("matchStatus", invoice.getMatchStatus());
                    return m;
                }
            } catch (Exception ignore) {
                // fall through to synthetic result
            }
        }
        if (invoice.getMatchStatus() == null || "NONE".equals(invoice.getMatchStatus())) {
            return null;
        }
        // 旧数据：仅返回状态与发票金额，提示重新执行匹配
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoiceId", invoice.getId());
        m.put("invoiceNo", invoice.getInvoiceNo());
        m.put("orderId", invoice.getOrderId());
        m.put("invoiceAmount", invoice.getTaxInclusiveAmount());
        m.put("matchStatus", invoice.getMatchStatus());
        m.put("incomplete", true);
        m.put("message", "历史结果仅保存状态，可点击「三单匹配」重新计算完整明细");
        return m;
    }

    /** 登记发票并关联采购订单 */
    @PostMapping
    @RequirePerm("fin:invoice:create")
    @Transactional(rollbackFor = Exception.class)
    public R<Invoice> register(@RequestBody RegisterReq req, @RequestAttribute("userId") Long userId) {
        if (req.getInvoiceNo() == null || req.getInvoiceNo().isBlank()) {
            throw new BizException("发票号码不能为空");
        }
        if (req.getOrderId() == null) {
            throw new BizException("必须关联采购订单");
        }
        if (req.getTaxInclusiveAmount() == null || req.getTaxInclusiveAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("含税金额必须大于0");
        }
        Long exists = invoiceMapper.selectCount(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getInvoiceNo, req.getInvoiceNo()));
        if (exists != null && exists > 0) {
            throw new BizException("发票号码已存在: " + req.getInvoiceNo());
        }
        PurchaseOrder order = orderMapper.selectById(req.getOrderId());
        if (order == null || (order.getIsDeleted() != null && order.getIsDeleted() == 1)) {
            throw new BizException("采购订单不存在");
        }

        BigDecimal incl = req.getTaxInclusiveAmount();
        BigDecimal tax = req.getTaxAmount() != null ? req.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal rate = req.getTaxRate() != null ? req.getTaxRate()
                : (order.getDefaultTaxRate() != null ? order.getDefaultTaxRate() : BigDecimal.ZERO);
        BigDecimal excl = incl.subtract(tax);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(req.getInvoiceNo());
        invoice.setInvoiceCode(req.getInvoiceCode());
        invoice.setInvoiceType(req.getInvoiceType() != null ? req.getInvoiceType() : "VAT_SPECIAL");
        invoice.setOrderId(order.getId());
        invoice.setSupplierId(order.getSupplierId());
        invoice.setInvoiceDate(req.getInvoiceDate() != null ? req.getInvoiceDate() : LocalDate.now());
        invoice.setTaxRate(rate);
        invoice.setTaxCode(req.getTaxCode());
        invoice.setTaxInclusiveAmount(incl.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxAmount(tax.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxExclusiveAmount(excl.setScale(2, RoundingMode.HALF_UP));
        invoice.setStatus("REGISTERED");
        invoice.setMatchStatus("NONE");
        invoice.setDeptId(deptResolver.resolveDeptId(userId, order.getDeptId()));
        invoice.setRemark(req.getRemark());
        invoice.setIsDeleted(0);
        invoice.setCreatedBy(userId);
        invoiceMapper.insert(invoice);
        return R.ok(invoice);
    }

    /** 三单匹配：订单含税 vs 入库金额 vs 发票金额差异分析 */
    @RequirePerm("fin:invoice:match")
    @PostMapping("/{id}/match")
    @Transactional(rollbackFor = Exception.class)
    public R<Map<String, Object>> match(@PathVariable Long id, @RequestAttribute("userId") Long userId) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null || (invoice.getIsDeleted() != null && invoice.getIsDeleted() == 1)) {
            throw new BizException("发票不存在");
        }
        PurchaseOrder order = orderMapper.selectById(invoice.getOrderId());
        if (order == null) {
            throw new BizException("关联采购订单不存在");
        }
        List<PurchaseOrderItem> orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, order.getId()));
        List<Long> orderItemIds = orderItems.stream().map(PurchaseOrderItem::getId).toList();

        BigDecimal orderAmount = nvl(order.getTaxInclusiveAmount());
        if (orderAmount.compareTo(BigDecimal.ZERO) == 0) {
            orderAmount = orderItems.stream()
                    .map(i -> nvl(i.getTaxInclusiveAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal inboundAmount = BigDecimal.ZERO;
        BigDecimal inboundQty = BigDecimal.ZERO;
        List<Map<String, Object>> inboundLines = new ArrayList<>();
        if (!orderItemIds.isEmpty()) {
            List<InboundOrderItem> inboundItems = inboundItemMapper.selectList(
                    new LambdaQueryWrapper<InboundOrderItem>()
                            .in(InboundOrderItem::getPurchaseOrderItemId, orderItemIds));
            for (InboundOrderItem ii : inboundItems) {
                BigDecimal cost = nvl(ii.getCostTaxIncl());
                if (cost.compareTo(BigDecimal.ZERO) == 0) {
                    cost = nvl(ii.getInQty()).multiply(nvl(ii.getTaxInclusivePrice()))
                            .setScale(2, RoundingMode.HALF_UP);
                }
                inboundAmount = inboundAmount.add(cost);
                inboundQty = inboundQty.add(nvl(ii.getInQty()));
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("inboundItemId", ii.getId());
                line.put("inboundId", ii.getInboundId());
                line.put("orderItemId", ii.getPurchaseOrderItemId());
                line.put("materialId", ii.getMaterialId());
                line.put("inQty", ii.getInQty());
                line.put("amount", cost);
                inboundLines.add(line);
            }
        }

        BigDecimal invoiceAmount = nvl(invoice.getTaxInclusiveAmount());
        BigDecimal orderQty = orderItems.stream().map(i -> nvl(i.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> diffs = new ArrayList<>();
        diffs.add(diff("ORDER_VS_INBOUND", orderAmount, inboundAmount));
        diffs.add(diff("ORDER_VS_INVOICE", orderAmount, invoiceAmount));
        diffs.add(diff("INBOUND_VS_INVOICE", inboundAmount, invoiceAmount));

        String matchStatus = "PASS";
        for (Map<String, Object> d : diffs) {
            Boolean ok = (Boolean) d.get("withinTolerance");
            if (ok == null || !ok) {
                BigDecimal rate = (BigDecimal) d.get("diffRate");
                boolean hardFail = rate != null && rate.abs().compareTo(new BigDecimal("5")) >= 0;
                matchStatus = hardFail ? "FAIL" : ("PASS".equals(matchStatus) ? "WARN" : matchStatus);
                if (hardFail) {
                    matchStatus = "FAIL";
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoiceId", invoice.getId());
        result.put("invoiceNo", invoice.getInvoiceNo());
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("supplierId", invoice.getSupplierId());
        result.put("amountTolerance", AMOUNT_TOLERANCE);
        result.put("rateTolerancePercent", RATE_TOLERANCE);
        result.put("orderAmount", orderAmount.setScale(2, RoundingMode.HALF_UP));
        result.put("inboundAmount", inboundAmount.setScale(2, RoundingMode.HALF_UP));
        result.put("invoiceAmount", invoiceAmount.setScale(2, RoundingMode.HALF_UP));
        result.put("orderQty", orderQty);
        result.put("inboundQty", inboundQty);
        result.put("diffs", diffs);
        result.put("inboundLines", inboundLines);
        result.put("matchStatus", matchStatus);
        result.put("matchedBy", userId);
        // LocalDate 不开 JavaTimeModule 会序列化失败 → 必须存字符串
        result.put("matchedAt", LocalDate.now().toString());

        invoice.setMatchStatus(matchStatus);
        invoice.setStatus("MATCHED".equals(matchStatus) || "WARN".equals(matchStatus) ? "MATCHED" : "MATCH_FAILED");
        String json;
        try {
            json = MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("invoiceId", invoice.getId());
            fallback.put("invoiceNo", invoice.getInvoiceNo());
            fallback.put("orderId", order.getId());
            fallback.put("orderNo", order.getOrderNo());
            fallback.put("orderAmount", result.get("orderAmount"));
            fallback.put("inboundAmount", result.get("inboundAmount"));
            fallback.put("invoiceAmount", result.get("invoiceAmount"));
            fallback.put("matchStatus", matchStatus);
            fallback.put("diffs", diffs);
            fallback.put("matchedAt", LocalDate.now().toString());
            try {
                json = MAPPER.writeValueAsString(fallback);
            } catch (Exception e2) {
                json = "{\"matchStatus\":\"" + matchStatus + "\"}";
            }
        }
        invoice.setMatchResult(json);
        invoice.setUpdatedBy(userId);
        invoiceMapper.updateById(invoice);

        return R.ok(result);
    }

    private Map<String, Object> diff(String pair, BigDecimal left, BigDecimal right) {
        BigDecimal d = left.subtract(right);
        BigDecimal rate = left.compareTo(BigDecimal.ZERO) == 0
                ? (right.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100))
                : d.multiply(BigDecimal.valueOf(100)).divide(left, 2, RoundingMode.HALF_UP);
        boolean amountOk = d.abs().compareTo(AMOUNT_TOLERANCE) <= 0;
        boolean rateOk = rate.abs().compareTo(RATE_TOLERANCE) <= 0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pair", pair);
        m.put("leftAmount", left.setScale(2, RoundingMode.HALF_UP));
        m.put("rightAmount", right.setScale(2, RoundingMode.HALF_UP));
        m.put("diff", d.setScale(2, RoundingMode.HALF_UP));
        m.put("diffRate", rate);
        m.put("withinTolerance", amountOk || rateOk);
        return m;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
