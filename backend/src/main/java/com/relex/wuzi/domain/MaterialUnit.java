package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计量单位字典：支持分类与相对基准单位的换算系数。
 */
@Data
@TableName("material_unit")
public class MaterialUnit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String unitCode;
    private String unitName;
    /** 分类：LENGTH 长度 / WEIGHT 重量 / COUNT 数量 / VOLUME 体积 */
    private String category;
    /** 相对基准单位换算系数 */
    private BigDecimal convertFactor;
    /** 基准单位编码（自身为基准时填自身） */
    private String baseUnitCode;
    private Integer sortNo;
    private Integer status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
