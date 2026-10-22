package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_batch")
public class StockBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private Long materialId;
    private Long warehouseId;
    private Long supplierId;
    private Long purchaseOrderId;
    private Long inboundOrderId;
    private LocalDate productionDate;
    private LocalDate expireDate;
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxExclusivePrice;
    private BigDecimal costTaxIncl;
    private BigDecimal costTaxExcl;
    private BigDecimal qty;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
