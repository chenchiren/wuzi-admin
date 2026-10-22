package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_order_item")
public class PurchaseOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long materialId;
    private Long supplierProductId;
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxExclusivePrice;
    private BigDecimal taxUnitAmount;
    private BigDecimal qty;
    private BigDecimal receivedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxExclusiveAmount;
    private BigDecimal taxAmount;
    private String currency;
    private String unit;
    private String priceSource;
    private Long priceSourceId;
    private java.time.LocalDate expectedArrivalDate;
    private String taxCalcFlag;
    private Integer version;
}
