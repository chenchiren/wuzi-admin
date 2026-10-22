package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_return_item")
public class PurchaseReturnItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnId;
    private Long purchaseOrderItemId;
    private Long inboundOrderItemId;
    private Long materialId;
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxExclusivePrice;
    private BigDecimal returnQty;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxExclusiveAmount;
    private BigDecimal taxAmount;
    private Integer amountSign;
}
