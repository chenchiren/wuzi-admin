package com.relex.wuzi.ai.tool.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单号查询：按单号前缀路由到 14 类单据头（采购/仓储/资产/合同）。
 * 返回单头关键字段 + 状态中文释义；不含明细行（P0 范围）。
 */
@Component
@RequiredArgsConstructor
public class QueryBillTool implements AiTool {

    private final ObjectMapper om;
    private final PurchaseApplyMapper purchaseApplyMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseInquiryMapper purchaseInquiryMapper;
    private final PurchaseArrivalMapper purchaseArrivalMapper;
    private final PurchaseReturnMapper purchaseReturnMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final InventoryCheckMapper inventoryCheckMapper;
    private final MaterialUseApplyMapper materialUseApplyMapper;
    private final BorrowOrderMapper borrowOrderMapper;
    private final ReturnStoreOrderMapper returnStoreOrderMapper;
    private final ScrapOrderMapper scrapOrderMapper;
    private final PurchaseContractMapper purchaseContractMapper;

    /** 单据状态 → 中文 */
    private static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry("DRAFT", "草稿"),
            Map.entry("SUBMITTED", "待审批"),
            Map.entry("PENDING_APPROVAL", "待审批"),
            Map.entry("PENDING", "待处理"),
            Map.entry("APPROVED", "已审批"),
            Map.entry("REJECTED", "已驳回"),
            Map.entry("COMPLETED", "已完成"),
            Map.entry("CANCELLED", "已作废"),
            Map.entry("RED_FLUSHED", "已红冲"),
            Map.entry("ISSUED", "已发料"),
            Map.entry("LENT", "借出中"),
            Map.entry("RECEIVED", "已到货"),
            Map.entry("PARTIAL", "部分完成"),
            Map.entry("PAID", "已付款"),
            Map.entry("ACTIVE", "生效中"),
            Map.entry("CLOSED", "已关闭"),
            Map.entry("TERMINATED", "已终止"),
            Map.entry("IN_TRANSIT", "运输中"));

    private static final Set<String> KEEP_KEYS = Set.of(
            "applyNo", "orderNo", "inquiryNo", "arrivalNo", "returnNo", "inboundNo", "outboundNo",
            "transferNo", "checkNo", "borrowNo", "scrapNo", "contractNo",
            "status", "planNo", "sourceNo",
            "applyTime", "applyDate", "orderDate", "inquireTime", "arrivalTime", "inboundTime",
            "outboundTime", "transferTime", "checkTime", "borrowDate", "expectReturnDate",
            "actualReturnDate", "scrapDate", "signDate", "startDate", "endDate", "needDate",
            "acceptTime", "confirmTime", "createdAt", "updatedAt",
            "totalTaxInclAmount", "taxInclusiveAmount", "taxExclusiveAmount", "taxAmount",
            "totalAmount", "contractAmount", "paidAmount",
            "title", "remark", "purpose", "useType", "borrowType");

    @Override
    public String name() {
        return "query_bill";
    }

    @Override
    public String label() {
        return "查询单据";
    }

    @Override
    public String description() {
        return "按单号查询单据当前状态与关键字段（支持采购申请PA/采购订单PO/询价IQ/到货AR/采购退货PR/入库IN/出库OUT或CK/调拨TF/盘点IC/领用UA/借用BO/退库RT/报废SP/合同HT）。用户给出具体单号时使用。";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("billNo", Map.of("type", "string", "description", "完整单号，如 RK20261001-01、PO20261001-02"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("billNo"));
        return schema;
    }

    @Override
    public boolean allowedFor(ToolContext ctx) {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String billNo = QueryStockTool.str(args, "billNo");
        if (billNo == null || billNo.isBlank()) {
            return ToolResult.of("单号为空", List.of(), "空单号");
        }
        String no = billNo.trim().toUpperCase();
        String prefix = no.replaceAll("[0-9].*$", "");

        Object entity = switch (prefix) {
            case "PA" -> purchaseApplyMapper.selectOne(one(PurchaseApply::getApplyNo, no));
            case "PO" -> purchaseOrderMapper.selectOne(one(PurchaseOrder::getOrderNo, no));
            case "IQ" -> purchaseInquiryMapper.selectOne(one(PurchaseInquiry::getInquiryNo, no));
            case "AR" -> purchaseArrivalMapper.selectOne(one(PurchaseArrival::getArrivalNo, no));
            case "PR" -> purchaseReturnMapper.selectOne(one(PurchaseReturn::getReturnNo, no));
            case "IN" -> inboundOrderMapper.selectOne(one(InboundOrder::getInboundNo, no));
            case "OUT", "CK" -> outboundOrderMapper.selectOne(one(OutboundOrder::getOutboundNo, no));
            case "TF" -> transferOrderMapper.selectOne(one(TransferOrder::getTransferNo, no));
            case "ICS", "IC" -> inventoryCheckMapper.selectOne(one(InventoryCheck::getCheckNo, no));
            case "UA" -> materialUseApplyMapper.selectOne(one(MaterialUseApply::getApplyNo, no));
            case "BO" -> borrowOrderMapper.selectOne(one(BorrowOrder::getBorrowNo, no));
            case "RT" -> returnStoreOrderMapper.selectOne(one(ReturnStoreOrder::getReturnNo, no));
            case "SP" -> scrapOrderMapper.selectOne(one(ScrapOrder::getScrapNo, no));
            case "HT" -> purchaseContractMapper.selectOne(one(PurchaseContract::getContractNo, no));
            default -> null;
        };

        if (entity == null) {
            return ToolResult.of("未查到单号 " + no + "（或该单号类型暂不支持查询）", List.of(), "未命中");
        }
        Map<String, Object> row = pick(om.convertValue(entity, Map.class));
        String status = String.valueOf(row.getOrDefault("status", ""));
        row.put("statusLabel", STATUS_LABELS.getOrDefault(status, status));
        List<Map<String, Object>> rows = List.of(row);
        return ToolResult.of("单据状态：" + row.get("statusLabel"), rows, "单号" + no + " 状态" + status);
    }

    private static <T> LambdaQueryWrapper<T> one(com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> col, String no) {
        return new LambdaQueryWrapper<T>().eq(col, no).last("LIMIT 1");
    }

    private static Map<String, Object> pick(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getValue() != null && KEEP_KEYS.contains(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unused")
    private List<Map<String, Object>> rowsOf(Map<String, Object> row) {
        return new ArrayList<>(List.of(row));
    }
}
