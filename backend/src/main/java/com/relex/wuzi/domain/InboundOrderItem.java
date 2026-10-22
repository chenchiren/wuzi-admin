package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("inbound_order_item")
public class InboundOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inboundId;
    private Long purchaseOrderItemId;
    private Long purchaseArrivalItemId;
    private Long materialId;
    private Long locationId;
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxExclusivePrice;
    private BigDecimal inQty;
    private BigDecimal costTaxIncl;
    private BigDecimal costTaxExcl;
    private BigDecimal taxAmount;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expireDate;
}
