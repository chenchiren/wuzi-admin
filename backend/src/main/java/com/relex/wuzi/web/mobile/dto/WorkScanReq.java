package com.relex.wuzi.web.mobile.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 手机端作业扫码请求 */
@Data
public class WorkScanReq {
    private String code;
    private BigDecimal qty;
    /** SET=覆盖本次收（默认）；ADD=累加箱数/包装；REMAIN=填到可收剩余。禁止默认扫一件 +1 */
    private String fillMode;
    private Long locationId;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expireDate;
}
