package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.InboundOrder;
import com.relex.wuzi.domain.InboundOrderItem;
import com.relex.wuzi.domain.PurchaseReturn;
import com.relex.wuzi.domain.PurchaseReturnItem;
import com.relex.wuzi.mapper.InboundOrderItemMapper;
import com.relex.wuzi.mapper.InboundOrderMapper;
import com.relex.wuzi.mapper.PurchaseReturnItemMapper;
import com.relex.wuzi.mapper.PurchaseReturnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购退货红冲闭环：确认退货时对库存做 redFlush（amount_sign=-1），
 * 并保证 stock_record 以 amount_sign=-1 落库。
 */
@Service
@RequiredArgsConstructor
public class ReturnRedFlushService {

    public static final String BIZ_PURCHASE_RETURN = "PURCHASE_RETURN";

    private final StockService stockService;
    private final PurchaseReturnMapper returnMapper;
    private final PurchaseReturnItemMapper returnItemMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundOrderItemMapper;

    /**
     * 对已审批通过的采购退货单执行库存红冲，单据状态置为 COMPLETED。
     * 库存减少（货已退供应商），流水 amount_sign=-1。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseReturn redFlushOnConfirm(Long returnId, Long warehouseId, Long operatorId) {
        PurchaseReturn ret = returnMapper.selectById(returnId);
        if (ret == null) {
            throw new BizException("退货单不存在");
        }
        if ("COMPLETED".equals(ret.getStatus()) || "RED_FLUSHED".equals(ret.getStatus())) {
            throw new BizException("退货单已红冲/完成，不可重复: " + ret.getStatus());
        }
        if (!"APPROVED".equals(ret.getStatus())) {
            throw new BizException("仅已审批通过的退货单可红冲确认，当前: " + ret.getStatus());
        }
        List<PurchaseReturnItem> items = returnItemMapper.selectList(new LambdaQueryWrapper<PurchaseReturnItem>()
                .eq(PurchaseReturnItem::getReturnId, returnId));
        if (items.isEmpty()) {
            throw new BizException("退货明细为空");
        }

        for (PurchaseReturnItem item : items) {
            BigDecimal qty = item.getReturnQty() == null ? BigDecimal.ZERO : item.getReturnQty();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("退货数量必须大于0");
            }
            Long whId = warehouseId != null ? warehouseId : resolveWarehouseId(item);
            if (whId == null) {
                throw new BizException("无法确定仓库，请指定 warehouseId");
            }
            BigDecimal price = item.getTaxInclusivePrice() != null ? item.getTaxInclusivePrice() : BigDecimal.ZERO;
            BigDecimal amount = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);

            StockService.StockChange change = new StockService.StockChange();
            change.setBizType(BIZ_PURCHASE_RETURN);
            change.setBizNo(ret.getReturnNo());
            change.setBizId(ret.getId());
            change.setWarehouseId(whId);
            change.setMaterialId(item.getMaterialId());
            change.setChangeQty(qty.negate());
            change.setTaxInclAmount(amount);
            change.setTaxRate(item.getTaxRate());
            change.setTaxInclPrice(price);
            change.setOperatorId(operatorId);
            change.setRemark("采购退货红冲");
            // redFlush 强制 amount_sign=-1
            stockService.redFlush(change);
        }

        ret.setStatus("COMPLETED");
        ret.setUpdatedAt(LocalDateTime.now());
        returnMapper.updateById(ret);
        return ret;
    }

    /** 从入库明细反查仓库 */
    private Long resolveWarehouseId(PurchaseReturnItem item) {
        if (item.getInboundOrderItemId() == null) {
            return null;
        }
        InboundOrderItem ii = inboundOrderItemMapper.selectById(item.getInboundOrderItemId());
        if (ii == null || ii.getInboundId() == null) {
            return null;
        }
        InboundOrder order = inboundOrderMapper.selectById(ii.getInboundId());
        return order == null ? null : order.getWarehouseId();
    }
}
