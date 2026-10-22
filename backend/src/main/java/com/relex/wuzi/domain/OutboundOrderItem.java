package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("outbound_order_item")
public class OutboundOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long outboundId;
    private Long materialId;
    private String batchNo;
    private Long locationId;
    private BigDecimal qty;
    private BigDecimal actualQty;
    private BigDecimal taxInclPrice;
    private BigDecimal taxRate;
}
