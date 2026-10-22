package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("supplier")
public class Supplier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String supplierCode;
    private String supplierName;
    private String creditCode;
    private String supplierType;
    private String contactName;
    private String contactPhone;
    private String email;
    private String address;
    private String bankName;
    private String bankAccount;
    private String defaultTaxCode;
    private BigDecimal defaultTaxRate;
    private String taxQualification;
    private String invoiceType;
    private Integer settlePriceIncl;
    private String settleType;
    private String payType;
    private String coopStatus;
    private String remark;
    private Integer isDeleted;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
