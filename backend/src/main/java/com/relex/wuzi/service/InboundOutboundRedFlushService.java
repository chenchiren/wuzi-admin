package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.InboundOrder;
import com.relex.wuzi.domain.InboundOrderItem;
import com.relex.wuzi.domain.OutboundOrder;
import com.relex.wuzi.domain.OutboundOrderItem;
import com.relex.wuzi.mapper.InboundOrderItemMapper;
import com.relex.wuzi.mapper.InboundOrderMapper;
import com.relex.wuzi.mapper.OutboundOrderItemMapper;
import com.relex.wuzi.mapper.OutboundOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库/出库红冲统一服务。
 * 出库红冲若已有实现，则此处补单据状态闭环（RED_FLUSHED）；入库红冲完整实现。
 */
@Service
@RequiredArgsConstructor
public class InboundOutboundRedFlushService {

    public static final String ST_RED_FLUSHED = "RED_FLUSHED";

    private final StockService stockService;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundItemMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderItemMapper outboundItemMapper;

    /**
     * 入库红冲：冲销已确认入库，库存扣减，流水 amount_sign=-1。
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundOrder redFlushInbound(Long inboundId, Long operatorId) {
        InboundOrder order = inboundOrderMapper.selectById(inboundId);
        if (order == null) {
            throw new BizException("入库单不存在");
        }
        if (ST_RED_FLUSHED.equals(order.getStatus())) {
            throw new BizException("入库单已红冲");
        }
        if (!"CONFIRMED".equals(order.getStatus())) {
            throw new BizException("仅已确认入库单可红冲，当前: " + order.getStatus());
        }
        List<InboundOrderItem> items = inboundItemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundId, inboundId));
        if (items.isEmpty()) {
            throw new BizException("入库明细为空");
        }
        for (InboundOrderItem item : items) {
            BigDecimal qty = item.getInQty() == null ? BigDecimal.ZERO : item.getInQty();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal price = item.getTaxInclusivePrice() != null ? item.getTaxInclusivePrice() : BigDecimal.ZERO;
            BigDecimal amount = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);

            StockService.StockChange change = new StockService.StockChange();
            change.setBizType("INBOUND_RED");
            change.setBizNo(order.getInboundNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getWarehouseId());
            change.setLocationId(item.getLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(item.getBatchNo());
            change.setChangeQty(qty.negate());
            change.setTaxInclAmount(amount);
            change.setCostTaxIncl(nvl(item.getCostTaxIncl()).negate());
            change.setCostTaxExcl(nvl(item.getCostTaxExcl()).negate());
            change.setTaxRate(item.getTaxRate());
            change.setTaxInclPrice(price);
            change.setOperatorId(operatorId);
            change.setRemark("入库红冲");
            change.setSupplierId(order.getSupplierId());
            change.setInboundOrderId(order.getId());
            stockService.redFlush(change);
        }
        order.setStatus(ST_RED_FLUSHED);
        order.setUpdatedAt(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
        return order;
    }

    /**
     * 出库红冲：冲销已确认出库，库存回补，流水 amount_sign=-1，并补单据状态 RED_FLUSHED。
     * 若外部已有红冲逻辑，可仅调用 {@link #completeOutboundRedFlushStatus} 补状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrder redFlushOutbound(Long outboundId, Long operatorId) {
        OutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        if (ST_RED_FLUSHED.equals(order.getStatus())) {
            // 已红冲则仅保证状态闭环
            return completeOutboundRedFlushStatus(outboundId);
        }
        if (!"CONFIRMED".equals(order.getStatus())) {
            throw new BizException("仅已确认出库单可红冲，当前: " + order.getStatus());
        }
        List<OutboundOrderItem> items = outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOutboundId, outboundId));
        for (OutboundOrderItem item : items) {
            BigDecimal qty = item.getActualQty() != null && item.getActualQty().compareTo(BigDecimal.ZERO) > 0
                    ? item.getActualQty() : item.getQty();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String batchNo = StockService.normalizeBatch(item.getBatchNo());
            BigDecimal taxInclPrice = item.getTaxInclPrice() != null ? item.getTaxInclPrice() : BigDecimal.ZERO;
            BigDecimal amount = taxInclPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);

            StockService.StockChange change = new StockService.StockChange();
            change.setBizType("OUTBOUND_RED");
            change.setBizNo(order.getOutboundNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getWarehouseId());
            change.setLocationId(item.getLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(batchNo);
            change.setChangeQty(qty);
            change.setTaxInclAmount(amount);
            change.setTaxRate(item.getTaxRate());
            change.setTaxInclPrice(taxInclPrice);
            change.setOperatorId(operatorId);
            change.setRemark("出库红冲回补");
            stockService.redFlush(change);
        }
        return completeOutboundRedFlushStatus(outboundId);
    }

    /** 出库红冲补单据状态：统一置为 RED_FLUSHED */
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrder completeOutboundRedFlushStatus(Long outboundId) {
        OutboundOrder order = outboundOrderMapper.selectById(outboundId);
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        if (!ST_RED_FLUSHED.equals(order.getStatus())) {
            order.setStatus(ST_RED_FLUSHED);
            order.setUpdatedAt(LocalDateTime.now());
            outboundOrderMapper.updateById(order);
        }
        return order;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
