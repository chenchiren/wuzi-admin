package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_plan")
public class PurchasePlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String planNo;
    private String title;
    private Integer planYear;
    private Integer planMonth;
    private Long deptId;
    private String projectCode;
    private String planType;
    private String status;
    private BigDecimal totalAmount;
    private String remark;
    private Integer isDeleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
