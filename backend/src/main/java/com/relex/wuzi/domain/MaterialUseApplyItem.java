package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 领用申请明细（material_use_apply_item）
 * 注意：表无 is_deleted / issued_qty，发料数量记录在 stock_record。
 */
@Data
@TableName("material_use_apply_item")
public class MaterialUseApplyItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applyId;
    private Long materialId;
    private BigDecimal qty;
    private String unit;
    private String remark;

    /** 本次发料/退库数量（不落库，仅接口传参） */
    @TableField(exist = false)
    private BigDecimal changeQty;
}
