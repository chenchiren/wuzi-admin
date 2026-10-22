package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_arrival_item")
public class PurchaseArrivalItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long arrivalId;
    private Long purchaseOrderItemId;
    private Long materialId;
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxExclusivePrice;
    private BigDecimal arriveQty;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxExclusiveAmount;
    private BigDecimal taxAmount;
}
