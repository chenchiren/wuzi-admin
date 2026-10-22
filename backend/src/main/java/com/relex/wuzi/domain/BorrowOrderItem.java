package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 借用单明细。表 borrow_order_item 已建（见 sql/wuzi_full.sql）。
 */
@Data
@TableName("borrow_order_item")
public class BorrowOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long borrowId;
    private Long materialId;
    private BigDecimal qty;
    private BigDecimal lentQty;
    private BigDecimal returnedQty;
    private String unit;
    private String remark;
}
