package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tax_rate_dict")
public class TaxRateDict {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taxCode;
    private String taxName;
    private BigDecimal taxRate;
    private String taxCategory;
    private Integer isDefault;
    private Integer isEnabled;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
