package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_flow_def")
public class ApprovalFlowDef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String flowCode;
    private String flowName;
    private String bizType;
    private Integer status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
