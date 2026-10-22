package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_order")
public class PurchaseOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long supplierId;
    private Long applyId;
    /** 来源询价（询价选定供应商生成订单时回写） */
    private Long inquiryId;
    private String inquiryNo;
    private Long purchaseUserId;
    private Long deptId;
    private java.time.LocalDate orderDate;
    private java.time.LocalDate expectArrivalDate;
    private String currency;
    private String defaultTaxCode;
    private BigDecimal defaultTaxRate;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxExclusiveAmount;
    private BigDecimal taxAmount;
    private String taxRateSummary;
    private String payType;
    private String settleType;
    private String status;
    private String remark;
    private Integer isDeleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
