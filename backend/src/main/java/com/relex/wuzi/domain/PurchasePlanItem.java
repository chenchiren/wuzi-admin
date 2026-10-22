package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_plan_item")
public class PurchasePlanItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long planId;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal qty;
    private BigDecimal estimatedPrice;
    private BigDecimal estimatedAmount;
    private String purpose;
    private Long demandDeptId;
    private String status;
    private Long applyId;
    private String remark;
    /** 转出采购申请单号（非表字段，列表展示用） */
    @TableField(exist = false)
    private String applyNo;
}
