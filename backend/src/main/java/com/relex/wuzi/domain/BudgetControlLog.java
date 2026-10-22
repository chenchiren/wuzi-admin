package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget_control_log")
public class BudgetControlLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long budgetId;
    private String action;
    private BigDecimal amount;
    private String bizType;
    private Long bizId;
    private BigDecimal beforeUsed;
    private BigDecimal beforeFrozen;
    private BigDecimal afterUsed;
    private BigDecimal afterFrozen;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
}
