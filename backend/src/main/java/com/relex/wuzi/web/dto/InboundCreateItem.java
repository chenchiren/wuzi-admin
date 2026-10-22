package com.relex.wuzi.web.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 入库明细行命令 */
@Data
public class InboundCreateItem {
    private Long materialId;
    private Long purchaseOrderItemId;
    private BigDecimal inQty;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxRate;
    private String taxCode;
    private Long locationId;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expireDate;
}
