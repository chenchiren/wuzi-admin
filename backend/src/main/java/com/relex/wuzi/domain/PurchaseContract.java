package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_contract")
public class PurchaseContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String contractNo;
    private String title;
    private String contractType;
    private Long supplierId;
    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal contractAmount;
    private BigDecimal paidAmount;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxAmount;
    private String status;
    private Long budgetId;
    private Long deptId;
    private Long ownerId;
    private String remark;
    private Integer isDeleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
