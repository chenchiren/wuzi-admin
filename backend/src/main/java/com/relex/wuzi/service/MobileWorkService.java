package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BillNoUtil;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import com.relex.wuzi.web.mobile.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 手机端简化作业用例：扫码入库/出库、到货提交、领用与审批、库存与脱敏溯源。
 * Controller 只做收参与权限声明；库存变更一律走 StockService。
 */
@Service
@RequiredArgsConstructor
public class MobileWorkService {

    private final TraceScanService scanService;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundItemMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderItemMapper outboundItemMapper;
    private final PurchaseArrivalMapper arrivalMapper;
    private final PurchaseArrivalItemMapper arrivalItemMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final MaterialUseApplyMapper applyMapper;
    private final MaterialUseApplyItemMapper applyItemMapper;
    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final StockMapper stockMapper;
    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final StockService stockService;
    private final ApprovalService approvalService;
    private final FlowApprovalService flowApprovalService;
    private final TraceQrCodeMapper qrMapper;
    private final BorrowOrderMapper borrowOrderMapper;
    private final BorrowOrderItemMapper borrowOrderItemMapper;
    private final BorrowReturnRecordMapper borrowReturnRecordMapper;

    // ---------- 入库 ----------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> inboundScan(Long id, WorkScanReq req,
                                           Long userId, String username, List<String> roles,
                                           Boolean hidePrice, String clientIp) {
        InboundOrder order = requireInbound(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BizException("仅草稿入库单可扫码，当前: " + order.getStatus());
        }
        TraceScanService.Resolved resolved = scanService.resolve(req.getCode());
        writeWorkScanLog(resolved, "INBOUND", id, order.getWarehouseId(), userId, username, clientIp);

        Map<String, Object> data = new LinkedHashMap<>(scanService.toView(resolved,
                TraceScanService.shouldHidePrice(hidePrice, roles)));
        if (!resolved.isSuccess()) {
            data.put("matched", false);
            return data;
        }

        InboundOrderItem item = matchInboundItem(id, resolved, req);
        if (item == null) {
            data.put("matched", false);
            data.put("hint", "码与入库单明细不匹配");
            return data;
        }
        BigDecimal planQty = resolvePlanQty(item);
        BigDecimal current = nvl(item.getInQty());
        BigDecimal filled;
        String fillMode = blank(req.getFillMode()) ? "SET" : req.getFillMode().trim().toUpperCase(Locale.ROOT);
        switch (fillMode) {
            case "ADD" -> {
                // 显式累加（如多箱），数量必填或默认包装 1；不是逐件扫码计数
                BigDecimal add = req.getQty() == null ? BigDecimal.ONE : req.getQty();
                if (add.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException("累加数量必须大于 0");
                }
                filled = current.add(add);
            }
            case "REMAIN" -> {
                filled = planQty != null ? planQty : (req.getQty() != null ? req.getQty() : current);
            }
            default -> {
                // SET：扫码定位行并带出数量；未传 qty 则填可收剩余，禁止默认 +1
                if (req.getQty() != null) {
                    filled = req.getQty();
                } else if (planQty != null) {
                    filled = planQty;
                } else {
                    filled = current;
                }
            }
        }
        if (filled.compareTo(BigDecimal.ZERO) < 0) {
            filled = BigDecimal.ZERO;
        }
        item.setInQty(filled);
        if (blank(req.getBatchNo()) && resolved.getBatchNo() != null) {
            item.setBatchNo(resolved.getBatchNo());
        } else if (!blank(req.getBatchNo())) {
            item.setBatchNo(req.getBatchNo().trim());
        }
        if (req.getLocationId() != null) {
            item.setLocationId(req.getLocationId());
        }
        if (req.getProductionDate() != null) {
            item.setProductionDate(req.getProductionDate());
        }
        if (req.getExpireDate() != null) {
            item.setExpireDate(req.getExpireDate());
        } else if (item.getExpireDate() == null && item.getBatchNo() != null) {
            Material m = materialMapper.selectById(item.getMaterialId());
            if (m != null && m.getShelfLifeDays() != null && m.getShelfLifeDays() > 0) {
                LocalDate base = item.getProductionDate() != null ? item.getProductionDate() : LocalDate.now();
                item.setExpireDate(base.plusDays(m.getShelfLifeDays()));
            }
        }
        inboundItemMapper.updateById(item);

        data.put("matched", true);
        data.put("item", item);
        data.put("fillMode", fillMode);
        data.put("filledQty", filled);
        data.put("addQty", filled.subtract(current));
        data.put("planQty", planQty);
        data.put("items", inboundItemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundId, id)));
        return data;
    }

    /**
     * 扫码反查待办：命中待收货入库单 / 借用中单据，避免多单平铺。
     */
    public Map<String, Object> lookupWork(WorkScanReq req,
                                          Long userId, String username, String clientIp) {
        TraceScanService.Resolved resolved = scanService.resolve(req.getCode());
        writeWorkScanLog(resolved, "LOOKUP", null, null, userId, username, clientIp);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resolved", scanService.toView(resolved, true));
        if (!resolved.isSuccess()) {
            data.put("pendingInbounds", List.of());
            data.put("activeBorrows", List.of());
            return data;
        }

        List<Map<String, Object>> inbounds = new ArrayList<>();
        List<InboundOrder> drafts = inboundOrderMapper.selectList(new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getStatus, "DRAFT")
                .orderByDesc(InboundOrder::getId)
                .last("LIMIT 50"));
        for (InboundOrder order : drafts) {
            InboundOrderItem hit = matchInboundItem(order.getId(), resolved, req);
            if (hit == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", order.getId());
            row.put("inboundNo", order.getInboundNo());
            row.put("sourceNo", order.getSourceNo());
            row.put("warehouseId", order.getWarehouseId());
            row.put("supplierId", order.getSupplierId());
            row.put("itemId", hit.getId());
            row.put("materialId", hit.getMaterialId());
            row.put("inQty", hit.getInQty());
            row.put("planQty", resolvePlanQty(hit));
            row.put("batchNo", hit.getBatchNo());
            inbounds.add(row);
        }
        data.put("pendingInbounds", inbounds);

        List<Map<String, Object>> borrows = findActiveBorrows(resolved);
        data.put("activeBorrows", borrows);
        return data;
    }

    /**
     * 扫码归还（件管/借出物资）：扫码即身份，不依赖借单号。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> borrowScanReturn(ScanReturnReq req,
                                                Long userId, String username, String clientIp) {
        if (req.getWarehouseId() == null) {
            throw new BizException("请指定归还仓库");
        }
        TraceScanService.Resolved resolved = scanService.resolve(req.getCode());
        writeWorkScanLog(resolved, "RETURN", null, req.getWarehouseId(), userId, username, clientIp);
        if (!resolved.isSuccess()) {
            throw new BizException(resolved.getFailReason() != null ? resolved.getFailReason() : "无法识别的码");
        }
        List<Map<String, Object>> candidates = findActiveBorrows(resolved);
        if (candidates.isEmpty()) {
            throw new BizException("未找到该物资的借用中单据");
        }
        Map<String, Object> first = candidates.get(0);
        Long borrowId = ((Number) first.get("id")).longValue();
        Long itemId = first.get("itemId") != null ? ((Number) first.get("itemId")).longValue() : null;

        BorrowOrder order = borrowOrderMapper.selectById(borrowId);
        if (order == null) {
            throw new BizException("借用单不存在");
        }
        BorrowOrderItem item = itemId != null ? borrowOrderItemMapper.selectById(itemId) : null;
        if (item == null) {
            item = borrowOrderItemMapper.selectOne(new LambdaQueryWrapper<BorrowOrderItem>()
                    .eq(BorrowOrderItem::getBorrowId, borrowId)
                    .eq(BorrowOrderItem::getMaterialId, resolved.getMaterialId())
                    .last("LIMIT 1"));
        }
        if (item == null) {
            throw new BizException("借用明细不存在");
        }
        BigDecimal remain = nvl(item.getLentQty()).subtract(nvl(item.getReturnedQty()));
        BigDecimal returnQty = req.getReturnQty() != null ? req.getReturnQty() : remain;
        if (returnQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("归还数量必须大于 0");
        }
        if (returnQty.compareTo(remain) > 0) {
            throw new BizException("归还数量超出未还: " + remain);
        }
        item.setReturnedQty(nvl(item.getReturnedQty()).add(returnQty));
        borrowOrderItemMapper.updateById(item);

        BorrowReturnRecord rec = new BorrowReturnRecord();
        rec.setBorrowId(order.getId());
        rec.setBorrowItemId(item.getId());
        rec.setMaterialId(item.getMaterialId());
        rec.setReturnQty(returnQty);
        rec.setConditionStatus(blank(req.getConditionStatus()) ? "GOOD" : req.getConditionStatus());
        rec.setOperatorId(userId);
        rec.setRemark(req.getRemark());
        rec.setReturnTime(LocalDateTime.now());
        rec.setCreatedAt(LocalDateTime.now());
        borrowReturnRecordMapper.insert(rec);

        StockService.StockChange change = new StockService.StockChange();
        change.setBizType("BORROW_RETURN");
        change.setBizNo(order.getBorrowNo());
        change.setBizId(order.getId());
        change.setWarehouseId(req.getWarehouseId());
        change.setMaterialId(item.getMaterialId());
        change.setChangeQty(returnQty);
        change.setOperatorId(userId);
        change.setRemark(blank(req.getRemark()) ? "扫码归还" : req.getRemark());
        stockService.increase(change);

        List<BorrowOrderItem> items = borrowOrderItemMapper.selectList(new LambdaQueryWrapper<BorrowOrderItem>()
                .eq(BorrowOrderItem::getBorrowId, borrowId));
        boolean allReturned = items.stream().allMatch(it ->
                nvl(it.getReturnedQty()).compareTo(nvl(it.getLentQty())) >= 0);
        order.setStatus(allReturned ? "RETURNED" : "PART_RETURNED");
        if (allReturned) {
            order.setActualReturnDate(LocalDate.now());
        }
        order.setUpdatedBy(userId);
        order.setUpdatedAt(LocalDateTime.now());
        borrowOrderMapper.updateById(order);

        return Map.of(
                "borrowNo", order.getBorrowNo(),
                "status", order.getStatus(),
                "returnQty", returnQty,
                "allReturned", allReturned
        );
    }

    private List<Map<String, Object>> findActiveBorrows(TraceScanService.Resolved resolved) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (resolved.getMaterialId() == null) {
            return out;
        }
        List<BorrowOrder> orders = borrowOrderMapper.selectList(new LambdaQueryWrapper<BorrowOrder>()
                .in(BorrowOrder::getStatus, "LENT", "PART_RETURNED", "OVERDUE")
                .orderByDesc(BorrowOrder::getId)
                .last("LIMIT 50"));
        LocalDate today = LocalDate.now();
        for (BorrowOrder order : orders) {
            List<BorrowOrderItem> items = borrowOrderItemMapper.selectList(new LambdaQueryWrapper<BorrowOrderItem>()
                    .eq(BorrowOrderItem::getBorrowId, order.getId())
                    .eq(BorrowOrderItem::getMaterialId, resolved.getMaterialId()));
            for (BorrowOrderItem it : items) {
                BigDecimal remain = nvl(it.getLentQty()).subtract(nvl(it.getReturnedQty()));
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", order.getId());
                row.put("borrowNo", order.getBorrowNo());
                row.put("status", order.getStatus());
                row.put("itemId", it.getId());
                row.put("materialId", it.getMaterialId());
                row.put("lentQty", it.getLentQty());
                row.put("returnedQty", it.getReturnedQty());
                row.put("remainQty", remain);
                row.put("borrowDate", order.getBorrowDate());
                row.put("expectReturnDate", order.getExpectReturnDate());
                row.put("applyUserId", order.getApplyUserId());
                long days = order.getBorrowDate() != null
                        ? java.time.temporal.ChronoUnit.DAYS.between(order.getBorrowDate(), today) : 0;
                row.put("borrowDays", days);
                row.put("overdue", order.getExpectReturnDate() != null && today.isAfter(order.getExpectReturnDate()));
                out.add(row);
            }
        }
        return out;
    }

    private BigDecimal resolvePlanQty(InboundOrderItem item) {
        if (item.getPurchaseOrderItemId() == null) {
            return null;
        }
        PurchaseOrderItem poi = purchaseOrderItemMapper.selectById(item.getPurchaseOrderItemId());
        return poi != null ? poi.getQty() : null;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> inboundConfirm(Long id, Long userId) {
        InboundOrder order = requireInbound(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BizException("仅草稿状态可确认入库，当前: " + order.getStatus());
        }
        List<InboundOrderItem> items = inboundItemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundId, id));
        if (items.isEmpty()) {
            throw new BizException("入库明细为空");
        }
        for (InboundOrderItem item : items) {
            if (nvl(item.getInQty()).compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            StockService.StockChange change = new StockService.StockChange();
            change.setBizType("INBOUND");
            change.setBizNo(order.getInboundNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getWarehouseId());
            change.setLocationId(item.getLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(item.getBatchNo());
            change.setChangeQty(item.getInQty());
            change.setTaxInclAmount(nvl(item.getCostTaxIncl()));
            change.setCostTaxIncl(nvl(item.getCostTaxIncl()));
            change.setCostTaxExcl(nvl(item.getCostTaxExcl()));
            change.setTaxRate(item.getTaxRate());
            change.setTaxInclPrice(item.getTaxInclusivePrice());
            change.setTaxExclusivePrice(item.getTaxExclusivePrice());
            change.setTaxCode(item.getTaxCode());
            change.setOperatorId(userId);
            change.setRemark("手机端扫码入库确认");
            change.setSupplierId(order.getSupplierId());
            change.setPurchaseOrderId(order.getSourceId());
            change.setInboundOrderId(order.getId());
            change.setProductionDate(item.getProductionDate());
            change.setExpireDate(item.getExpireDate());
            stockService.increase(change);

            if (item.getBatchNo() != null && !item.getBatchNo().isBlank()) {
                TraceQrCode qr = qrMapper.selectOne(new LambdaQueryWrapper<TraceQrCode>()
                        .eq(TraceQrCode::getMaterialId, item.getMaterialId())
                        .eq(TraceQrCode::getBatchNo, item.getBatchNo())
                        .last("LIMIT 1"));
                if (qr != null) {
                    if ("CREATED".equals(qr.getStatus())) {
                        qr.setStatus("ACTIVE");
                        qr.setActivatedAt(LocalDateTime.now());
                    }
                    qr.setCurrentWarehouseId(order.getWarehouseId());
                    qr.setCurrentLocationId(item.getLocationId());
                    qr.setInboundOrderId(order.getId());
                    qrMapper.updateById(qr);
                }
            }
        }
        order.setStatus("CONFIRMED");
        order.setUpdatedAt(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
        return Map.of("order", inboundOrderMapper.selectById(id), "items", items);
    }

    public Map<String, Object> inboundDetail(Long id) {
        InboundOrder order = requireInbound(id);
        return Map.of("order", order,
                "items", inboundItemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                        .eq(InboundOrderItem::getInboundId, id)));
    }

    // ---------- 出库 ----------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> outboundScan(Long id, WorkScanReq req,
                                            Long userId, String username, List<String> roles,
                                            Boolean hidePrice, String clientIp) {
        OutboundOrder order = requireOutbound(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BizException("仅草稿出库单可扫码，当前: " + order.getStatus());
        }
        TraceScanService.Resolved resolved = scanService.resolve(req.getCode());
        writeWorkScanLog(resolved, "OUTBOUND", id, order.getWarehouseId(), userId, username, clientIp);

        Map<String, Object> data = new LinkedHashMap<>(scanService.toView(resolved,
                TraceScanService.shouldHidePrice(hidePrice, roles)));
        if (!resolved.isSuccess()) {
            data.put("matched", false);
            return data;
        }

        OutboundOrderItem item = matchOutboundItem(id, resolved, req);
        if (item == null) {
            data.put("matched", false);
            data.put("hint", "码与出库单明细不匹配");
            return data;
        }
        BigDecimal add = req.getQty() == null ? BigDecimal.ONE : req.getQty();
        if (add.compareTo(BigDecimal.ZERO) > 0) {
            item.setActualQty(nvl(item.getActualQty()).add(add));
        }
        if (!blank(req.getBatchNo())) {
            item.setBatchNo(req.getBatchNo().trim());
        } else if (blank(item.getBatchNo()) && resolved.getBatchNo() != null) {
            item.setBatchNo(resolved.getBatchNo());
        }
        if (req.getLocationId() != null) {
            item.setLocationId(req.getLocationId());
        }
        outboundItemMapper.updateById(item);

        BigDecimal available = stockService.availableQty(order.getWarehouseId(), item.getMaterialId(),
                StockService.normalizeBatch(item.getBatchNo()));
        data.put("matched", true);
        data.put("item", item);
        data.put("addQty", add);
        data.put("availableQty", available);
        data.put("items", outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, id)));
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> outboundConfirm(Long id, Long userId) {
        OutboundOrder order = requireOutbound(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BizException("仅草稿状态可确认出库，当前: " + order.getStatus());
        }
        List<OutboundOrderItem> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, id));
        if (items.isEmpty()) {
            throw new BizException("出库明细为空");
        }
        for (OutboundOrderItem item : items) {
            BigDecimal outQty = item.getActualQty() != null && item.getActualQty().compareTo(BigDecimal.ZERO) > 0
                    ? item.getActualQty() : item.getQty();
            if (outQty == null || outQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
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
            change.setRemark("手机端扫码出库确认");
            if (stock != null) {
                BigDecimal qtyBefore = nvl(stock.getQty());
                BigDecimal costIncl = qtyBefore.compareTo(BigDecimal.ZERO) > 0
                        ? nvl(stock.getCostTaxIncl()).multiply(outQty).divide(qtyBefore, 2, java.math.RoundingMode.HALF_UP)
                        : taxInclAmount;
                change.setCostTaxIncl(costIncl.negate());
                BigDecimal costExcl = qtyBefore.compareTo(BigDecimal.ZERO) > 0
                        ? nvl(stock.getCostTaxExcl()).multiply(outQty).divide(qtyBefore, 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                change.setCostTaxExcl(costExcl.negate());
            }
            stockService.decrease(change);
            item.setActualQty(outQty);
            outboundItemMapper.updateById(item);
        }
        order.setStatus("CONFIRMED");
        order.setUpdatedAt(LocalDateTime.now());
        outboundOrderMapper.updateById(order);
        return Map.of("order", outboundOrderMapper.selectById(id), "items", items);
    }

    public Map<String, Object> outboundDetail(Long id, List<String> roles, Boolean hidePrice) {
        OutboundOrder order = requireOutbound(id);
        List<OutboundOrderItem> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, id));
        boolean hide = TraceScanService.shouldHidePrice(hidePrice, roles);
        if (hide) {
            items.forEach(i -> {
                i.setTaxInclPrice(null);
                i.setTaxRate(null);
            });
        }
        return Map.of("order", order, "items", items);
    }

    // ---------- 到货 ----------

    public Map<String, Object> arrivalDetail(Long id, List<String> roles, Boolean hidePrice) {
        PurchaseArrival arrival = arrivalMapper.selectById(id);
        if (arrival == null) {
            throw new BizException("到货单不存在");
        }
        List<PurchaseArrivalItem> items = arrivalItemMapper.selectList(new LambdaQueryWrapper<PurchaseArrivalItem>()
                .eq(PurchaseArrivalItem::getArrivalId, id));
        boolean hide = TraceScanService.shouldHidePrice(hidePrice, roles);
        if (hide) {
            items.forEach(i -> {
                i.setTaxInclusivePrice(null);
                i.setTaxExclusivePrice(null);
                i.setTaxInclusiveAmount(null);
                i.setTaxExclusiveAmount(null);
                i.setTaxAmount(null);
                i.setTaxRate(null);
            });
        }
        Map<String, Object> data = new HashMap<>();
        data.put("arrival", arrival);
        data.put("items", items);
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseArrival arrivalSubmit(Long id, Long userId) {
        PurchaseArrival arrival = arrivalMapper.selectById(id);
        if (arrival == null) {
            throw new BizException("到货单不存在");
        }
        if (!ApprovalService.ST_DRAFT.equals(arrival.getStatus())
                && !ApprovalService.ST_REJECTED.equals(arrival.getStatus())) {
            throw new BizException("仅草稿/驳回状态可提交，当前: " + arrival.getStatus());
        }
        List<PurchaseArrivalItem> items = arrivalItemMapper.selectList(new LambdaQueryWrapper<PurchaseArrivalItem>()
                .eq(PurchaseArrivalItem::getArrivalId, id));
        if (items.isEmpty()) {
            throw new BizException("到货明细不能为空");
        }
        // 进入审批流，审批通过后再由 complete 接口完成收货
        flowApprovalService.start(ApprovalService.BIZ_ARRIVAL, ApprovalService.BIZ_ARRIVAL,
                id, arrival.getArrivalNo(), userId == null ? 0L : userId,
                "到货验收-" + arrival.getArrivalNo(),
                status -> {
                    arrival.setStatus(status);
                    arrivalMapper.updateById(arrival);
                });
        return arrival;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseArrival arrivalComplete(Long id) {
        PurchaseArrival arrival = arrivalMapper.selectById(id);
        if (arrival == null) {
            throw new BizException("到货单不存在");
        }
        if (!ApprovalService.ST_APPROVED.equals(arrival.getStatus())) {
            throw new BizException("仅审批通过的到货单可确认完成，当前: " + arrival.getStatus());
        }
        List<PurchaseArrivalItem> items = arrivalItemMapper.selectList(new LambdaQueryWrapper<PurchaseArrivalItem>()
                .eq(PurchaseArrivalItem::getArrivalId, id));
        if (items.isEmpty()) {
            throw new BizException("到货明细不能为空");
        }
        for (PurchaseArrivalItem item : items) {
            PurchaseOrderItem oi = purchaseOrderItemMapper.selectById(item.getPurchaseOrderItemId());
            if (oi == null) {
                continue;
            }
            BigDecimal arrive = nvl(item.getArriveQty());
            oi.setReceivedQty(nvl(oi.getReceivedQty()).add(arrive));
            if (oi.getQualifiedQty() == null) {
                oi.setQualifiedQty(arrive);
            }
            purchaseOrderItemMapper.updateById(oi);
        }
        arrival.setStatus(ApprovalService.ST_COMPLETED);
        arrivalMapper.updateById(arrival);
        return arrival;
    }

    // ---------- 领用 / 审批 ----------

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createUse(UseCreateReq req, Long userId) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BizException("领用明细不能为空");
        }
        MaterialUseApply apply = new MaterialUseApply();
        apply.setApplyNo(BillNoUtil.next("UA"));
        apply.setApplyUserId(userId == null ? 0L : userId);
        apply.setUseType(req.getUseType() == null ? "DAILY" : req.getUseType());
        apply.setProjectName(req.getProjectName());
        apply.setNeedDate(req.getNeedDate());
        apply.setPurpose(req.getPurpose());
        apply.setStatus("DRAFT");
        apply.setIsDeleted(0);
        applyMapper.insert(apply);

        List<MaterialUseApplyItem> items = new ArrayList<>();
        for (UseCreateItem it : req.getItems()) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            if (it.getQty() == null || it.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("申请数量必须大于 0");
            }
            MaterialUseApplyItem item = new MaterialUseApplyItem();
            item.setApplyId(apply.getId());
            item.setMaterialId(it.getMaterialId());
            item.setQty(it.getQty());
            item.setUnit(it.getUnit() != null ? it.getUnit() : material.getUnit());
            item.setRemark(it.getRemark());
            applyItemMapper.insert(item);
            items.add(item);
        }
        flowApprovalService.start(ApprovalService.BIZ_MATERIAL_USE, ApprovalService.BIZ_MATERIAL_USE,
                apply.getId(), apply.getApplyNo(), userId == null ? 0L : userId,
                "领用申请-" + apply.getApplyNo(),
                status -> {
                    apply.setStatus(status);
                    applyMapper.updateById(apply);
                });
        return Map.of("apply", apply, "items", items);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> approve(Long id, CommentReq req, Long userId) {
        String comment = req != null ? (req.getComment() != null ? req.getComment() : req.getRemark()) : null;
        ApprovalInstance inst = approvalInstanceMapper.selectById(id);
        if (inst != null) {
            if (flowApprovalService.hasFlow(id)) {
                flowApprovalService.approve(id, userId, comment, null);
            } else {
                approvalService.approve(id, userId, comment, status -> updateBizStatus(inst, status));
            }
            return Map.of("type", "APPROVAL", "instance", approvalInstanceMapper.selectById(id));
        }
        MaterialUseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("审批对象不存在");
        }
        if (!"SUBMITTED".equals(apply.getStatus())
                && !ApprovalService.ST_PENDING.equals(apply.getStatus())) {
            throw new BizException("仅已提交的领用可审批，当前: " + apply.getStatus());
        }
        apply.setStatus("APPROVED");
        apply.setFlowRemark(comment);
        applyMapper.updateById(apply);
        return Map.of("type", "MATERIAL_USE", "apply", apply);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reject(Long id, CommentReq req, Long userId) {
        String comment = req != null ? (req.getComment() != null ? req.getComment() : req.getRemark()) : null;
        ApprovalInstance inst = approvalInstanceMapper.selectById(id);
        if (inst != null) {
            if (flowApprovalService.hasFlow(id)) {
                flowApprovalService.reject(id, userId, comment, null);
            } else {
                approvalService.reject(id, userId, comment, status -> updateBizStatus(inst, status));
            }
            return Map.of("type", "APPROVAL", "instance", approvalInstanceMapper.selectById(id));
        }
        MaterialUseApply apply = applyMapper.selectById(id);
        if (apply == null) {
            throw new BizException("审批对象不存在");
        }
        if (!"SUBMITTED".equals(apply.getStatus())
                && !ApprovalService.ST_PENDING.equals(apply.getStatus())) {
            throw new BizException("仅已提交的领用可驳回，当前: " + apply.getStatus());
        }
        apply.setStatus("REJECTED");
        apply.setFlowRemark(comment);
        applyMapper.updateById(apply);
        return Map.of("type", "MATERIAL_USE", "apply", apply);
    }

    // ---------- 查询 / 溯源 ----------

    public PageResult<Map<String, Object>> stocks(long page, long size, Long materialId, Long warehouseId,
                                                  String keyword, List<String> roles, Boolean hidePrice) {
        boolean hide = TraceScanService.shouldHidePrice(hidePrice, roles);
        LambdaQueryWrapper<Stock> qw = new LambdaQueryWrapper<Stock>()
                .eq(materialId != null, Stock::getMaterialId, materialId)
                .eq(warehouseId != null, Stock::getWarehouseId, warehouseId)
                .orderByDesc(Stock::getId);
        Page<Stock> p = stockMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Stock s : p.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stockId", s.getId());
            row.put("warehouseId", s.getWarehouseId());
            row.put("locationId", s.getLocationId());
            row.put("materialId", s.getMaterialId());
            row.put("batchNo", s.getBatchNo());
            row.put("qty", s.getQty());
            row.put("frozenQty", s.getFrozenQty());
            Material m = materialMapper.selectById(s.getMaterialId());
            if (m != null) {
                row.put("materialCode", m.getMaterialCode());
                row.put("materialName", m.getMaterialName());
                row.put("spec", m.getSpec());
                row.put("unit", m.getUnit());
                if (keyword != null && !keyword.isBlank()) {
                    String k = keyword.toLowerCase(Locale.ROOT);
                    boolean hit = (m.getMaterialCode() != null && m.getMaterialCode().toLowerCase(Locale.ROOT).contains(k))
                            || (m.getMaterialName() != null && m.getMaterialName().toLowerCase(Locale.ROOT).contains(k));
                    if (!hit) {
                        continue;
                    }
                }
            }
            if (!hide) {
                row.put("costTaxIncl", s.getCostTaxIncl());
                row.put("costTaxExcl", s.getCostTaxExcl());
                row.put("lastTaxInclPrice", s.getLastTaxInclPrice());
                row.put("lastTaxRate", s.getLastTaxRate());
            }
            rows.add(row);
        }
        return PageResult.of(p.getTotal(), rows);
    }

    public Map<String, Object> trace(String traceToken, Long userId, List<String> roles, Boolean hidePrice) {
        TraceQrCode qr = qrMapper.selectOne(new LambdaQueryWrapper<TraceQrCode>()
                .eq(TraceQrCode::getTraceToken, traceToken).last("LIMIT 1"));
        if (qr == null) {
            throw new BizException(404, "溯源码不存在");
        }
        qr.setScanCount(qr.getScanCount() == null ? 1 : qr.getScanCount() + 1);
        qr.setLastScanAt(LocalDateTime.now());
        if (qr.getFirstScanAt() == null) {
            qr.setFirstScanAt(LocalDateTime.now());
        }
        qrMapper.updateById(qr);

        boolean hide = userId == null || TraceScanService.shouldHidePrice(hidePrice, roles);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("materialName", qr.getMaterialName());
        data.put("materialCode", qr.getMaterialCode());
        data.put("spec", qr.getSpec());
        data.put("unit", qr.getUnit());
        data.put("batchNo", qr.getBatchNo());
        data.put("serialNo", qr.getSerialNo());
        data.put("qrType", qr.getQrType());
        data.put("status", qr.getStatus());
        data.put("expireDate", qr.getExpireDate());
        data.put("activatedAt", qr.getActivatedAt());
        data.put("scanCount", qr.getScanCount());
        data.put("firstScan", qr.getFirstScanAt() != null);
        data.put("currentWarehouseId", qr.getCurrentWarehouseId());
        data.put("purchaseOrderId", qr.getPurchaseOrderId());
        data.put("inboundOrderId", qr.getInboundOrderId());
        if (!hide) {
            if (qr.getSupplierId() != null) {
                Supplier sp = supplierMapper.selectById(qr.getSupplierId());
                if (sp != null) {
                    data.put("supplierName", sp.getSupplierName());
                }
            }
            data.put("supplierId", qr.getSupplierId());
            data.put("priceVisible", true);
        } else {
            data.put("supplierMasked", qr.getSupplierId() != null);
        }
        return data;
    }

    // ---------- helpers ----------

    private void writeWorkScanLog(TraceScanService.Resolved resolved, String bizType, Long bizId,
                                  Long warehouseId, Long userId, String username, String clientIp) {
        TraceScanService.ScanContext ctx = new TraceScanService.ScanContext();
        ctx.setOperatorId(userId);
        ctx.setOperatorName(username);
        ctx.setChannel(TraceScanService.CHANNEL_H5);
        ctx.setScanType("WORK");
        ctx.setBizType(bizType);
        ctx.setBizId(bizId);
        ctx.setWarehouseId(warehouseId);
        if (clientIp != null) {
            ctx.setClientIp(clientIp);
        }
        scanService.writeLog(resolved, ctx);
    }

    private InboundOrderItem matchInboundItem(Long inboundId, TraceScanService.Resolved resolved, WorkScanReq req) {
        List<InboundOrderItem> items = inboundItemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundId, inboundId));
        Long materialId = resolved.getMaterialId();
        String batch = resolved.getBatchNo();
        if (!blank(req.getBatchNo())) {
            batch = req.getBatchNo().trim();
        }
        InboundOrderItem byBatch = null;
        InboundOrderItem byMaterial = null;
        for (InboundOrderItem item : items) {
            if (materialId != null && materialId.equals(item.getMaterialId())) {
                if (batch != null && batch.equals(item.getBatchNo())) {
                    return item;
                }
                if (byMaterial == null) {
                    byMaterial = item;
                }
                if (batch != null && blank(item.getBatchNo())) {
                    byBatch = item;
                }
            }
        }
        return byBatch != null ? byBatch : byMaterial;
    }

    private OutboundOrderItem matchOutboundItem(Long outboundId, TraceScanService.Resolved resolved, WorkScanReq req) {
        List<OutboundOrderItem> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, outboundId));
        Long materialId = resolved.getMaterialId();
        String batch = resolved.getBatchNo();
        if (!blank(req.getBatchNo())) {
            batch = req.getBatchNo().trim();
        }
        OutboundOrderItem byBatch = null;
        OutboundOrderItem byMaterial = null;
        for (OutboundOrderItem item : items) {
            if (materialId != null && materialId.equals(item.getMaterialId())) {
                if (batch != null && batch.equals(item.getBatchNo())) {
                    return item;
                }
                if (byMaterial == null) {
                    byMaterial = item;
                }
                if (batch != null && blank(item.getBatchNo())) {
                    byBatch = item;
                }
            }
        }
        return byBatch != null ? byBatch : byMaterial;
    }

    private void updateBizStatus(ApprovalInstance inst, String status) {
        flowApprovalService.applyBizStatus(inst.getBizType(), inst.getBizId(), status);
    }

    private InboundOrder requireInbound(Long id) {
        InboundOrder order = inboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("入库单不存在");
        }
        return order;
    }

    private OutboundOrder requireOutbound(Long id) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        return order;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
