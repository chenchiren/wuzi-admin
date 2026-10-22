package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_contract_item")
public class PurchaseContractItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long contractId;
    private Long materialId;
    private String materialName;
    private String spec;
    private String unit;
    private BigDecimal qty;
    private BigDecimal price;
    private BigDecimal taxRate;
    private BigDecimal taxExclusiveAmount;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxAmount;
    private String remark;
}
