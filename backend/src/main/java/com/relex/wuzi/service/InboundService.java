package com.relex.wuzi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.common.BillNoUtil;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.domain.*;
import com.relex.wuzi.mapper.*;
import com.relex.wuzi.web.dto.InboundCreateCmd;
import com.relex.wuzi.web.dto.InboundCreateItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 入库用例编排：创建（继承税价/反推可入量）、确认入账、作废。
 * Controller 只做收参与权限声明。
 */
@Service
@RequiredArgsConstructor
public class InboundService {

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundItemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final SupplierMapper supplierMapper;
    private final PriceTaxCalculator calculator;
    private final StockService stockService;

    public PageResult<InboundOrder> page(long page, long size, String status, Long warehouseId) {
        LambdaQueryWrapper<InboundOrder> qw = new LambdaQueryWrapper<InboundOrder>()
                .eq(status != null && !status.isBlank(), InboundOrder::getStatus, status)
                .eq(warehouseId != null, InboundOrder::getWarehouseId, warehouseId)
                .orderByDesc(InboundOrder::getId);
        Page<InboundOrder> p = inboundOrderMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    public Map<String, Object> detail(Long id) {
        InboundOrder order = requireOrder(id);
        List<InboundOrderItem> items = inboundItemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundId, id));
        return Map.of("order", order, "items", items);
    }

    @Transactional(rollbackFor = Exception.class)
    public InboundOrder cancel(Long id) {
        InboundOrder order = requireOrder(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BizException("仅草稿可作废，当前: " + order.getStatus());
        }
        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(InboundCreateCmd req, Long userId) {
        if (req.getWarehouseId() == null || warehouseMapper.selectById(req.getWarehouseId()) == null) {
            throw new BizException("仓库不存在");
        }
        Long supplierId = req.getSupplierId();
        PurchaseOrder po = null;
        List<InboundCreateItem> createItems = req.getItems();

        if (req.getPurchaseOrderId() != null) {
            po = purchaseOrderMapper.selectById(req.getPurchaseOrderId());
            if (po == null) {
                throw new BizException("采购订单不存在");
            }
            if (!"CONFIRMED".equals(po.getStatus()) && !"ARRIVED".equals(po.getStatus())
                    && !"PART_RECEIVED".equals(po.getStatus()) && !"RECEIVED".equals(po.getStatus())
                    && !"APPROVED".equals(po.getStatus())
                    && !"DRAFT".equals(po.getStatus())) {
                throw new BizException("采购订单状态不允许入库: " + po.getStatus());
            }
            supplierId = po.getSupplierId();
            if (createItems == null || createItems.isEmpty()) {
                createItems = resolveLinesFromOrder(po, req.getLocationId());
            }
        }
        if (createItems == null || createItems.isEmpty()) {
            throw new BizException("入库明细不能为空");
        }
        if (supplierId != null && supplierMapper.selectById(supplierId) == null) {
            throw new BizException("供应商不存在");
        }

        InboundOrder order = new InboundOrder();
        order.setInboundNo(BillNoUtil.next("IN"));
        order.setInboundType("PURCHASE");
        order.setWarehouseId(req.getWarehouseId());
        order.setSupplierId(supplierId);
        if (po != null) {
            order.setSourceId(po.getId());
            order.setSourceNo(po.getOrderNo());
        }
        order.setOperatorId(userId);
        order.setInboundTime(LocalDateTime.now());
        order.setStatus("DRAFT");
        order.setRemark(req.getRemark());
        order.setIsDeleted(0);
        inboundOrderMapper.insert(order);

        List<InboundOrderItem> items = saveItems(order, createItems, req.getLocationId());
        return Map.of("order", order, "items", items);
    }

    /**
     * 按采购订单反推可入库明细（可单测）。
     * 可入库上限 = 订单数量 − 历史已入。
     */
    public List<InboundCreateItem> resolveLinesFromOrder(PurchaseOrder po, Long defaultLocationId) {
        List<InboundCreateItem> createItems = new ArrayList<>();
        List<PurchaseOrderItem> poItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, po.getId()));
        for (PurchaseOrderItem poi : poItems) {
            BigDecimal alreadyIn = inboundItemMapper.selectList(
                            new LambdaQueryWrapper<InboundOrderItem>()
                                    .eq(InboundOrderItem::getPurchaseOrderItemId, poi.getId()))
                    .stream().map(x -> nvl(x.getInQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remain = nvl(poi.getQty()).subtract(alreadyIn);
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            InboundCreateItem it = new InboundCreateItem();
            it.setMaterialId(poi.getMaterialId());
            it.setPurchaseOrderItemId(poi.getId());
            it.setInQty(remain);
            it.setLocationId(defaultLocationId);
            it.setTaxInclusivePrice(poi.getTaxInclusivePrice());
            it.setTaxRate(poi.getTaxRate());
            it.setTaxCode(poi.getTaxCode());
            createItems.add(it);
        }
        return createItems;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(Long id, Long userId) {
        InboundOrder order = requireOrder(id);
        if (!"DRAFT".equals(order.getStatus())) {
            throw new BizException("仅草稿状态可确认入库，当前: " + order.getStatus());
        }
        List<InboundOrderItem> items = inboundItemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getInboundId, id));
        if (items.isEmpty()) {
            throw new BizException("入库明细为空");
        }

        for (InboundOrderItem item : items) {
            Material material = materialMapper.selectById(item.getMaterialId());
            StockService.StockChange change = new StockService.StockChange();
            change.setBizType("INBOUND");
            change.setBizNo(order.getInboundNo());
            change.setBizId(order.getId());
            change.setWarehouseId(order.getWarehouseId());
            change.setLocationId(item.getLocationId());
            change.setMaterialId(item.getMaterialId());
            change.setBatchNo(item.getBatchNo());
            change.setChangeQty(item.getInQty());
            change.setTaxInclAmount(item.getCostTaxIncl());
            change.setCostTaxIncl(item.getCostTaxIncl());
            change.setCostTaxExcl(item.getCostTaxExcl());
            change.setTaxRate(item.getTaxRate());
            change.setTaxInclPrice(item.getTaxInclusivePrice());
            change.setTaxExclusivePrice(item.getTaxExclusivePrice());
            change.setTaxCode(item.getTaxCode());
            change.setOperatorId(userId);
            change.setRemark("采购入库确认");
            change.setSupplierId(order.getSupplierId());
            change.setPurchaseOrderId(order.getSourceId());
            change.setInboundOrderId(order.getId());
            change.setProductionDate(item.getProductionDate());
            change.setExpireDate(item.getExpireDate());
            stockService.increase(change);

            if (material != null && material.getSerialManaged() != null && material.getSerialManaged() == 1
                    && item.getBatchNo() != null) {
                stockService.bindSerial(item.getBatchNo(), item.getMaterialId(), order.getWarehouseId(),
                        item.getLocationId(), item.getBatchNo(), userId);
            }

            if (item.getPurchaseOrderItemId() != null && item.getPurchaseOrderItemId() > 0) {
                PurchaseOrderItem poi = purchaseOrderItemMapper.selectById(item.getPurchaseOrderItemId());
                if (poi != null) {
                    poi.setReceivedQty(nvl(poi.getReceivedQty()).add(nvl(item.getInQty())));
                    poi.setQualifiedQty(nvl(poi.getQualifiedQty()).add(nvl(item.getInQty())));
                    purchaseOrderItemMapper.updateById(poi);
                }
            }
        }

        if (order.getSourceId() != null) {
            PurchaseOrder po = purchaseOrderMapper.selectById(order.getSourceId());
            if (po != null) {
                List<PurchaseOrderItem> poItems = purchaseOrderItemMapper.selectList(
                        new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, po.getId()));
                boolean allReceived = true;
                for (PurchaseOrderItem poi : poItems) {
                    if (nvl(poi.getReceivedQty()).compareTo(nvl(poi.getQty())) < 0) {
                        allReceived = false;
                        break;
                    }
                }
                po.setStatus(allReceived ? "RECEIVED" : "PART_RECEIVED");
                purchaseOrderMapper.updateById(po);
            }
        }

        order.setStatus("CONFIRMED");
        order.setUpdatedAt(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
        return Map.of("order", inboundOrderMapper.selectById(id), "items", items);
    }

    private List<InboundOrderItem> saveItems(InboundOrder order, List<InboundCreateItem> createItems, Long defaultLocationId) {
        List<InboundOrderItem> items = new ArrayList<>();
        for (InboundCreateItem it : createItems) {
            Material material = materialMapper.selectById(it.getMaterialId());
            if (material == null) {
                throw new BizException("物资不存在: " + it.getMaterialId());
            }
            BigDecimal price = it.getTaxInclusivePrice();
            BigDecimal rate = it.getTaxRate();
            if (it.getPurchaseOrderItemId() != null) {
                PurchaseOrderItem poi = purchaseOrderItemMapper.selectById(it.getPurchaseOrderItemId());
                if (poi != null) {
                    if (price == null) {
                        price = poi.getTaxInclusivePrice();
                    }
                    if (rate == null) {
                        rate = poi.getTaxRate();
                    }
                }
            }
            if (rate == null) {
                rate = material.getRefTaxRate() != null ? material.getRefTaxRate() : new BigDecimal("13");
            }
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                price = material.getRefPrice();
            }
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("缺少含税单价: " + material.getMaterialName());
            }
            if (it.getInQty() == null || it.getInQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("入库数量必须大于0");
            }

            PriceTaxCalculator.Line line = calculator.fromInclusive(price, rate, it.getInQty());
            InboundOrderItem item = new InboundOrderItem();
            item.setInboundId(order.getId());
            item.setPurchaseOrderItemId(it.getPurchaseOrderItemId() != null ? it.getPurchaseOrderItemId() : 0L);
            item.setMaterialId(it.getMaterialId());
            item.setLocationId(it.getLocationId() != null ? it.getLocationId() : defaultLocationId);
            item.setTaxCode(it.getTaxCode());
            item.setTaxRate(line.taxRate);
            item.setTaxInclusivePrice(line.taxInclusivePrice);
            item.setTaxExclusivePrice(line.taxExclusivePrice);
            item.setInQty(it.getInQty());
            item.setCostTaxIncl(line.taxInclusiveAmount);
            item.setCostTaxExcl(line.taxExclAmount);
            item.setTaxAmount(line.taxAmount);
            item.setProductionDate(it.getProductionDate());
            item.setExpireDate(resolveExpire(it, material));
            item.setBatchNo(resolveBatchNo(it, material, order.getInboundNo(), items.size() + 1));
            inboundItemMapper.insert(item);
            items.add(item);
        }
        return items;
    }

    private InboundOrder requireOrder(Long id) {
        InboundOrder order = inboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException("入库单不存在");
        }
        return order;
    }

    private String resolveBatchNo(InboundCreateItem it, Material material, String inboundNo, int lineNo) {
        if (it.getBatchNo() != null && !it.getBatchNo().isBlank()) {
            return it.getBatchNo().trim();
        }
        boolean batchManaged = material.getBatchManaged() != null && material.getBatchManaged() == 1;
        if (!batchManaged) {
            return null;
        }
        return "B" + LocalDate.now().toString().replace("-", "") + "-"
                + inboundNo.substring(Math.max(0, inboundNo.length() - 6)) + lineNo;
    }

    private LocalDate resolveExpire(InboundCreateItem it, Material material) {
        if (it.getExpireDate() != null) {
            return it.getExpireDate();
        }
        if (material.getShelfLifeDays() != null && material.getShelfLifeDays() > 0) {
            LocalDate base = it.getProductionDate() != null ? it.getProductionDate() : LocalDate.now();
            return base.plusDays(material.getShelfLifeDays());
        }
        return null;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
