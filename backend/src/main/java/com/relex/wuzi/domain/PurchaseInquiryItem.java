package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("purchase_inquiry_item")
public class PurchaseInquiryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inquiryId;
    private Long materialId;
    private Long supplierId;
    private BigDecimal qty;
    private String unit;
    private String taxCode;
    private BigDecimal taxRate;
    private BigDecimal taxInclusivePrice;
    private BigDecimal taxExclusivePrice;
    private BigDecimal taxUnitAmount;
    private BigDecimal taxInclusiveAmount;
    private BigDecimal taxExclusiveAmount;
    private BigDecimal taxAmount;
    private Integer isSelected;
    private String priceSource;
    private String remark;
}
