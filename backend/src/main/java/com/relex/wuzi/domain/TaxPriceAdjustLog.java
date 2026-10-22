package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tax_price_adjust_log")
public class TaxPriceAdjustLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String adjustType;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;
}
