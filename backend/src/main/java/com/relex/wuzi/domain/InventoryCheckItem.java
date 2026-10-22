package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 盘点明细。表 inventory_check_item 已建（见 sql/wuzi_full.sql）。
 */
@Data
@TableName("inventory_check_item")
public class InventoryCheckItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long checkId;
    private Long materialId;
    private String batchNo;
    private Long locationId;
    private BigDecimal bookQty;
    private BigDecimal actualQty;
    private BigDecimal diffQty;
    private BigDecimal taxInclPrice;
    private String remark;
}
