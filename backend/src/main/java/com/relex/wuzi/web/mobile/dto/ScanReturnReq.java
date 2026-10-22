package com.relex.wuzi.web.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 扫码归还请求（件管/借出物资） */
@Data
public class ScanReturnReq {
    private String code;
    private Long warehouseId;
    private String conditionStatus;
    private String remark;
    private BigDecimal returnQty;
}
