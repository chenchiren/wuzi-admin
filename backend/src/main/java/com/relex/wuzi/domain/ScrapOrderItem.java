package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 报废单明细。表 scrap_order_item 已建（见 sql/wuzi_full.sql）。
 */
@Data
@TableName("scrap_order_item")
public class ScrapOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scrapId;
    private Long materialId;
    private BigDecimal qty;
    private BigDecimal confirmedQty;
    private String unit;
    private String reason;
    private String remark;
}
