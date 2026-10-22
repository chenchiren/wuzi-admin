package com.relex.wuzi.web.mobile.dto;

import lombok.Data;

/** 审批意见请求（comment/remark 二者取其一） */
@Data
public class CommentReq {
    private String comment;
    private String remark;
}
