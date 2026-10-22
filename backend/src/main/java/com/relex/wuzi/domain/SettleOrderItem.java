package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("settle_order_item")
public class SettleOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long settleId;
    private Long orderItemId;
    private Long materialId;
    private BigDecimal settleQty;
    private BigDecimal settleAmount;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxAmount;
    private String remark;
}
