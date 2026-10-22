package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.domain.Stock;
import com.relex.wuzi.domain.StockBatch;
import com.relex.wuzi.domain.StockRecord;
import com.relex.wuzi.domain.StockSerial;
import com.relex.wuzi.mapper.StockBatchMapper;
import com.relex.wuzi.mapper.StockMapper;
import com.relex.wuzi.mapper.StockRecordMapper;
import com.relex.wuzi.mapper.StockSerialMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存核心：入库增加、出库校验可用量并扣减、红冲。
 * amount_sign 规则：1=正向(入库/增加)，-1=反向(出库/红冲)。
 * change_qty 为带符号的实际库存变动量（正增负减）；tax_inclAmount 为绝对值，用 amount_sign 表示加减。
 * 乐观锁：stock.version 条件更新，冲突抛 BizException。
 */
@Service
@RequiredArgsConstructor
public class StockService {

    public static final String BATCH_NONE = "";

    public static final int SIGN_POSITIVE = 1;
    public static final int SIGN_NEGATIVE = -1;

    private final StockMapper stockMapper;
    private final StockBatchMapper stockBatchMapper;
    private final StockSerialMapper stockSerialMapper;
    private final StockRecordMapper stockRecordMapper;

    @Data
    public static class StockChange {
        private String bizType;
        private String bizNo;
        private Long bizId;
        private Long warehouseId;
        private Long locationId;
        private Long materialId;
        private String batchNo;
        private String serialNo;
        /** 实际库存变动量，正增负减 */
        private BigDecimal changeQty;
        /** 含税金额绝对值 */
        private BigDecimal taxInclAmount = BigDecimal.ZERO;
        private BigDecimal costTaxIncl = BigDecimal.ZERO;
        private BigDecimal costTaxExcl = BigDecimal.ZERO;
        private BigDecimal taxRate;
        private BigDecimal taxInclPrice;
        private Long operatorId;
        private String remark;
        private Long supplierId;
        private Long purchaseOrderId;
        private Long inboundOrderId;
        private LocalDate productionDate;
        private LocalDate expireDate;
        private String taxCode;
        private BigDecimal taxExclusivePrice;
        /** 1=正向, -1=红冲/反向；出库默认 -1 */
        private Integer amountSign;
    }

    /** 入库增加库存 + 写流水（amount_sign=1） */
    @Transactional(rollbackFor = Exception.class)
    public StockRecord increase(StockChange cmd) {
        BigDecimal delta = requireNonZero(cmd.getChangeQty());
        if (delta.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("入库数量必须大于0");
        }
        cmd.setAmountSign(SIGN_POSITIVE);
        return applyChange(cmd, delta);
    }

    /** 出库校验可用量并扣减 + 写流水（amount_sign=-1） */
    @Transactional(rollbackFor = Exception.class)
    public StockRecord decrease(StockChange cmd) {
        BigDecimal qty = requireNonZero(cmd.getChangeQty()).abs();
        cmd.setChangeQty(qty.negate());
        cmd.setAmountSign(SIGN_NEGATIVE);
        return applyChange(cmd, qty.negate());
    }

    /**
     * 红冲：反向冲销，amount_sign=-1。
     * changeQty>0 表示红冲后库存增加（如冲销原出库），changeQty<0 表示红冲后库存减少（如冲销原入库）。
     */
    @Transactional(rollbackFor = Exception.class)
    public StockRecord redFlush(StockChange cmd) {
        BigDecimal delta = requireNonZero(cmd.getChangeQty());
        cmd.setAmountSign(SIGN_NEGATIVE);
        return applyChange(cmd, delta);
    }

    /**
     * 有符号变更（正增负减），未指定批次时自动挑选库存行。
     * 业务侧（领用/借用/报废/退库等）统一走此入口，禁止在 Controller 旁路改库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public StockRecord changeAuto(StockChange cmd) {
        BigDecimal delta = requireNonZero(cmd.getChangeQty());
        if (cmd.getBatchNo() == null || cmd.getBatchNo().isBlank()) {
            Stock best = pickCandidate(cmd, delta);
            if (best != null) {
                cmd.setBatchNo(best.getBatchNo() == null ? BATCH_NONE : best.getBatchNo());
                if (cmd.getLocationId() == null) {
                    cmd.setLocationId(best.getLocationId());
                }
            }
        }
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            cmd.setChangeQty(delta);
            return increase(cmd);
        }
        cmd.setChangeQty(delta.abs());
        return decrease(cmd);
    }

    /** 未指定批次时挑选可用库存行：扣减选数量够的行，增加优先有量或无批次行 */
    private Stock pickCandidate(StockChange cmd, BigDecimal signedDelta) {
        List<Stock> candidates = stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getWarehouseId, cmd.getWarehouseId())
                .eq(Stock::getMaterialId, cmd.getMaterialId())
                .eq(cmd.getLocationId() != null, Stock::getLocationId, cmd.getLocationId())
                .orderByDesc(Stock::getQty)
                .last("LIMIT 20"));
        if (candidates.isEmpty()) {
            return null;
        }
        for (Stock s : candidates) {
            BigDecimal q = nvl(s.getQty());
            if (signedDelta.compareTo(BigDecimal.ZERO) < 0) {
                if (q.add(signedDelta).compareTo(BigDecimal.ZERO) >= 0) {
                    return s;
                }
            } else if (q.compareTo(BigDecimal.ZERO) > 0
                    || s.getBatchNo() == null || s.getBatchNo().isBlank()) {
                return s;
            }
        }
        return signedDelta.compareTo(BigDecimal.ZERO) >= 0 ? candidates.get(0) : null;
    }

    public BigDecimal availableQty(Long warehouseId, Long materialId, String batchNo) {
        Stock stock = findStock(warehouseId, materialId, batchNo);
        if (stock == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal qty = stock.getQty() == null ? BigDecimal.ZERO : stock.getQty();
        BigDecimal frozen = stock.getFrozenQty() == null ? BigDecimal.ZERO : stock.getFrozenQty();
        return qty.subtract(frozen);
    }

    public Stock findStock(Long warehouseId, Long materialId, String batchNo) {
        return findStock(warehouseId, null, materialId, batchNo);
    }

    /** 按仓库(+库位)+物资+批次找库存；locationId 空则不限制库位（兼容旧数据） */
    public Stock findStock(Long warehouseId, Long locationId, Long materialId, String batchNo) {
        String bn = normalizeBatch(batchNo);
        return stockMapper.selectOne(new LambdaQueryWrapper<Stock>()
                .eq(Stock::getWarehouseId, warehouseId)
                .eq(Stock::getMaterialId, materialId)
                .eq(Stock::getBatchNo, bn)
                .eq(locationId != null, Stock::getLocationId, locationId)
                .orderByDesc(Stock::getQty)
                .last("LIMIT 1"));
    }

    public List<Stock> listStock(Long warehouseId, Long materialId) {
        return stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .eq(warehouseId != null, Stock::getWarehouseId, warehouseId)
                .eq(materialId != null, Stock::getMaterialId, materialId)
                .orderByDesc(Stock::getId));
    }

    private StockRecord applyChange(StockChange cmd, BigDecimal signedDelta) {
        if (cmd.getWarehouseId() == null || cmd.getMaterialId() == null) {
            throw new BizException("仓库/物资不能为空");
        }
        if (cmd.getBizType() == null || cmd.getBizType().isBlank()) {
            throw new BizException("业务类型不能为空");
        }
        String batchNo = normalizeBatch(cmd.getBatchNo());
        Stock stock = findStock(cmd.getWarehouseId(), cmd.getLocationId(), cmd.getMaterialId(), batchNo);
        BigDecimal before;
        if (stock == null) {
            if (signedDelta.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("库存不存在，无法扣减: 物资" + cmd.getMaterialId());
            }
            stock = new Stock();
            stock.setWarehouseId(cmd.getWarehouseId());
            stock.setLocationId(cmd.getLocationId());
            stock.setMaterialId(cmd.getMaterialId());
            stock.setBatchNo(batchNo);
            stock.setQty(BigDecimal.ZERO);
            stock.setFrozenQty(BigDecimal.ZERO);
            stock.setCostTaxIncl(BigDecimal.ZERO);
            stock.setCostTaxExcl(BigDecimal.ZERO);
            stock.setVersion(0);
            stock.setIsDeleted(0);
            stockMapper.insert(stock);
            before = BigDecimal.ZERO;
        } else {
            before = stock.getQty() == null ? BigDecimal.ZERO : stock.getQty();
            BigDecimal frozen = stock.getFrozenQty() == null ? BigDecimal.ZERO : stock.getFrozenQty();
            BigDecimal available = before.subtract(frozen);
            if (signedDelta.compareTo(BigDecimal.ZERO) < 0 && available.compareTo(signedDelta.abs()) < 0) {
                throw new BizException("可用库存不足: 可用" + available + "，需" + signedDelta.abs());
            }
        }

        BigDecimal after = before.add(signedDelta);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("库存不能为负");
        }

        BigDecimal costIncl = nvl(cmd.getCostTaxIncl());
        BigDecimal costExcl = nvl(cmd.getCostTaxExcl());
        int rows = stockMapper.update(null, new LambdaUpdateWrapper<Stock>()
                .eq(Stock::getId, stock.getId())
                .eq(Stock::getVersion, stock.getVersion() == null ? 0 : stock.getVersion())
                .set(Stock::getLocationId, cmd.getLocationId() != null ? cmd.getLocationId() : stock.getLocationId())
                .set(Stock::getQty, after)
                .set(Stock::getLastTaxRate, cmd.getTaxRate())
                .set(Stock::getLastTaxInclPrice, cmd.getTaxInclPrice())
                .setSql("version = IFNULL(version, 0) + 1")
                .setSql("cost_tax_incl = GREATEST(IFNULL(cost_tax_incl, 0) + {0}, 0)", costIncl)
                .setSql("cost_tax_excl = GREATEST(IFNULL(cost_tax_excl, 0) + {0}, 0)", costExcl));
        if (rows == 0) {
            throw new BizException("库存并发更新冲突，请重试");
        }

        if (signedDelta.compareTo(BigDecimal.ZERO) != 0) {
            upsertBatch(cmd, batchNo, signedDelta, costIncl, costExcl);
        }

        StockRecord record = new StockRecord();
        record.setBizType(cmd.getBizType());
        record.setBizNo(cmd.getBizNo());
        record.setBizId(cmd.getBizId());
        record.setWarehouseId(cmd.getWarehouseId());
        record.setLocationId(cmd.getLocationId());
        record.setMaterialId(cmd.getMaterialId());
        record.setBatchNo(batchNo.isEmpty() ? null : batchNo);
        record.setSerialNo(cmd.getSerialNo());
        record.setChangeQty(signedDelta);
        record.setBeforeQty(before);
        record.setAfterQty(after);
        record.setAmountSign(cmd.getAmountSign() != null ? cmd.getAmountSign()
                : (signedDelta.compareTo(BigDecimal.ZERO) >= 0 ? SIGN_POSITIVE : SIGN_NEGATIVE));
        record.setTaxInclAmount(nvl(cmd.getTaxInclAmount()));
        record.setOperatorId(cmd.getOperatorId());
        record.setRemark(cmd.getRemark());
        stockRecordMapper.insert(record);
        return record;
    }

    private void upsertBatch(StockChange cmd, String batchNo, BigDecimal signedDelta,
                             BigDecimal costIncl, BigDecimal costExcl) {
        if (batchNo.isEmpty()) {
            return;
        }
        StockBatch batch = stockBatchMapper.selectOne(new LambdaQueryWrapper<StockBatch>()
                .eq(StockBatch::getBatchNo, batchNo)
                .eq(StockBatch::getWarehouseId, cmd.getWarehouseId())
                .eq(StockBatch::getMaterialId, cmd.getMaterialId()));
        if (batch == null) {
            if (signedDelta.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("批次不存在: " + batchNo);
            }
            batch = new StockBatch();
            batch.setBatchNo(batchNo);
            batch.setMaterialId(cmd.getMaterialId());
            batch.setWarehouseId(cmd.getWarehouseId());
            batch.setSupplierId(cmd.getSupplierId());
            batch.setPurchaseOrderId(cmd.getPurchaseOrderId());
            batch.setInboundOrderId(cmd.getInboundOrderId());
            batch.setProductionDate(cmd.getProductionDate());
            batch.setExpireDate(cmd.getExpireDate());
            batch.setTaxCode(cmd.getTaxCode());
            batch.setTaxRate(cmd.getTaxRate());
            batch.setTaxInclusivePrice(cmd.getTaxInclPrice());
            batch.setTaxExclusivePrice(cmd.getTaxExclusivePrice());
            batch.setCostTaxIncl(costIncl.max(BigDecimal.ZERO));
            batch.setCostTaxExcl(costExcl.max(BigDecimal.ZERO));
            batch.setQty(signedDelta.max(BigDecimal.ZERO));
            batch.setIsDeleted(0);
            stockBatchMapper.insert(batch);
            return;
        }
        BigDecimal newQty = nvl(batch.getQty()).add(signedDelta);
        if (newQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("批次可用数量不足: " + batchNo);
        }
        stockBatchMapper.update(null, new LambdaUpdateWrapper<StockBatch>()
                .eq(StockBatch::getId, batch.getId())
                .set(StockBatch::getQty, newQty)
                .set(StockBatch::getTaxRate, cmd.getTaxRate() != null ? cmd.getTaxRate() : batch.getTaxRate())
                .set(StockBatch::getTaxInclusivePrice, cmd.getTaxInclPrice() != null ? cmd.getTaxInclPrice() : batch.getTaxInclusivePrice())
                .setSql("cost_tax_incl = GREATEST(IFNULL(cost_tax_incl, 0) + {0}, 0)", costIncl)
                .setSql("cost_tax_excl = GREATEST(IFNULL(cost_tax_excl, 0) + {0}, 0)", costExcl));
    }

    public void bindSerial(String serialNo, Long materialId, Long warehouseId, Long locationId,
                           String batchNo, Long userId) {
        if (serialNo == null || serialNo.isBlank()) {
            return;
        }
        StockSerial serial = stockSerialMapper.selectOne(new LambdaQueryWrapper<StockSerial>()
                .eq(StockSerial::getSerialNo, serialNo));
        if (serial == null) {
            serial = new StockSerial();
            serial.setSerialNo(serialNo);
            serial.setMaterialId(materialId);
            serial.setStatus("IN_STOCK");
            serial.setIsDeleted(0);
        }
        serial.setWarehouseId(warehouseId);
        serial.setLocationId(locationId);
        serial.setBatchNo(normalizeBatch(batchNo).isEmpty() ? null : normalizeBatch(batchNo));
        serial.setUserId(userId);
        if (serial.getId() == null) {
            stockSerialMapper.insert(serial);
        } else {
            stockSerialMapper.updateById(serial);
        }
    }

    public void releaseSerial(String serialNo) {
        if (serialNo == null || serialNo.isBlank()) {
            return;
        }
        StockSerial serial = stockSerialMapper.selectOne(new LambdaQueryWrapper<StockSerial>()
                .eq(StockSerial::getSerialNo, serialNo));
        if (serial != null) {
            serial.setStatus("OUT_STOCK");
            serial.setWarehouseId(null);
            serial.setLocationId(null);
            stockSerialMapper.updateById(serial);
        }
    }

    public static String normalizeBatch(String batchNo) {
        return batchNo == null ? BATCH_NONE : batchNo.trim();
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal requireNonZero(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) {
            throw new BizException("数量不能为0");
        }
        return v;
    }
}
