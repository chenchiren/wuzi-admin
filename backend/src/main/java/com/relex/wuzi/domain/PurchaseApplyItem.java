package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_apply_item")
public class PurchaseApplyItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long applyId;
    private Long materialId;
    private BigDecimal qty;
    private String unit;
    private BigDecimal refPrice;
    /** 税种编码快照；手填税率时为空 */
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclAmount;
    private String remark;
}
