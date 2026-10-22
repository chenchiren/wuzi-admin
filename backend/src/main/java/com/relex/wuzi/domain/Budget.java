package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget")
public class Budget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String budgetNo;
    private Integer budgetYear;
    private Long deptId;
    private String projectCode;
    private String category;
    private BigDecimal budgetAmount;
    private BigDecimal usedAmount;
    private BigDecimal frozenAmount;
    private String status;
    private String remark;
    private Integer isDeleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
