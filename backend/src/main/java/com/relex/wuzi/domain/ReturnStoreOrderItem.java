package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退库单明细：申请数 / 验收数 / 实收数。
 */
@Data
@TableName("return_store_order_item")
public class ReturnStoreOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnId;
    private Long materialId;
    private String batchNo;
    private Long locationId;
    private BigDecimal applyQty;
    private BigDecimal acceptQty;
    private BigDecimal actualQty;
    private BigDecimal taxInclPrice;
    private BigDecimal taxRate;
    private String conditionStatus;
    private String remark;
}
