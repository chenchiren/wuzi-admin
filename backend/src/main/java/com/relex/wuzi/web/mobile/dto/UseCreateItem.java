package com.relex.wuzi.web.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 领用申请明细 */
@Data
public class UseCreateItem {
    private Long materialId;
    private BigDecimal qty;
    private String unit;
    private String remark;
}
