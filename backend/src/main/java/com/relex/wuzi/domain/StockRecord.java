package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水。amount_sign: 1=正向(入库/增加), -1=反向(出库/红冲)。
 * change_qty 为实际库存变动量（正增负减）。
 */
@Data
@TableName("stock_record")
public class StockRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizNo;
    private Long bizId;
    private Long warehouseId;
    private Long locationId;
    private Long materialId;
    private String batchNo;
    private String serialNo;
    private BigDecimal changeQty;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;
    private Integer amountSign;
    private BigDecimal taxInclAmount;
    private Long operatorId;
    private String remark;
    private LocalDateTime createdAt;
}
