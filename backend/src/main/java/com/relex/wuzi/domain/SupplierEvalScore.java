package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("supplier_eval_score")
public class SupplierEvalScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long supplierId;
    private LocalDate evalDate;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal onTimeRate;
    private BigDecimal returnRate;
    private BigDecimal priceScore;
    private BigDecimal weightOnTime;
    private BigDecimal weightReturn;
    private BigDecimal weightPrice;
    private BigDecimal totalScore;
    private Integer orderCount;
    private Integer returnCount;
    private String detailJson;
    private Long createdBy;
    private LocalDateTime createdAt;
}
