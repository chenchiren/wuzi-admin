package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 调拨明细。表 transfer_order_item 已建（见 sql/wuzi_full.sql）。
 */
@Data
@TableName("transfer_order_item")
public class TransferOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transferId;
    private Long materialId;
    private String batchNo;
    private Long fromLocationId;
    private Long toLocationId;
    private BigDecimal qty;
    private BigDecimal actualQty;
    private BigDecimal taxInclPrice;
    private BigDecimal taxRate;
}
