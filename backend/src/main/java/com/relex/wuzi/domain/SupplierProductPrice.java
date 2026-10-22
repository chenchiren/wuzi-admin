package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("supplier_product_price")
public class SupplierProductPrice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierProductId;
    private Long supplierId;
    private Long materialId;
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxExclusivePrice;
    private BigDecimal taxUnitAmount;
    private String currency;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String sourceType;
    private Long sourceId;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
