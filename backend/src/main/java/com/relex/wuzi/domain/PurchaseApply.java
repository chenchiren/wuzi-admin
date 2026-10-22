package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_apply")
public class PurchaseApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 来源采购计划（计划行转申请时回写） */
    private Long planId;
    private String planNo;
    private String applyNo;
    private Long applyUserId;
    private Long deptId;
    private LocalDateTime applyTime;
    private LocalDate needDate;
    private String urgency;
    private String purpose;
    private String status;
    private BigDecimal totalTaxInclAmount;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
