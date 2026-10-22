package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("invoice")
public class Invoice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String invoiceNo;
    private String invoiceCode;
    private String invoiceType;
    private Long orderId;
    private Long supplierId;
    private LocalDate invoiceDate;
    private BigDecimal taxRate;
    /** 票面税种编码快照；手填时为空 */
    private String taxCode;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxExclusiveAmount;
    private BigDecimal taxAmount;
    private String status;
    private String matchStatus;
    private String matchResult;
    private Long deptId;
    private String remark;
    private Integer isDeleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
