package com.relex.wuzi.web.mobile.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 手机端领用申请创建请求 */
@Data
public class UseCreateReq {
    private String useType;
    private String projectName;
    private LocalDate needDate;
    private String purpose;
    private String remark;
    private List<UseCreateItem> items;
}
