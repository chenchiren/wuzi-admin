package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("material")
public class Material {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialCode;
    private String materialName;
    private Long categoryId;
    private String spec;
    private String brand;
    private String unit;
    private String barcode;
    private Integer batchManaged;
    private Integer serialManaged;
    private String qrGranularity;
    private Integer shelfLifeDays;
    private BigDecimal safetyStock;
    private BigDecimal maxStock;
    private BigDecimal refPrice;
    private BigDecimal refTaxRate;
    private Integer status;
    private String remark;
    private Integer isDeleted;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
