package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("stock")
public class Stock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private Long locationId;
    private Long materialId;
    private String batchNo;
    private BigDecimal qty;
    private BigDecimal frozenQty;
    private BigDecimal costTaxIncl;
    private BigDecimal costTaxExcl;
    private BigDecimal lastTaxRate;
    private BigDecimal lastTaxInclPrice;
    @Version
    private Integer version;
    private Integer isDeleted;
    private LocalDateTime updatedAt;
}
