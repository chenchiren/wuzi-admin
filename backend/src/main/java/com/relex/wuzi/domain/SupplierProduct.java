package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("supplier_product")
public class SupplierProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private Long materialId;
    private String supplierProductCode;
    private String supplierSku;
    private String productName;
    private String spec;
    private String brand;
    private String unit;
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxExclusivePrice;
    private BigDecimal taxUnitAmount;
    private String priceTaxMode;
    private BigDecimal moq;
    private BigDecimal minQty;
    private Integer leadTimeDays;
    private String packSpec;
    private Integer shelfLifeDays;
    private String currency;
    private Integer status;
    private Integer isDeleted;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
