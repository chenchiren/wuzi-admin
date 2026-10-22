package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批实例流程扩展：跟踪 flow_id / 当前节点 / 当前指定审批人。
 * 对应表 approval_instance_ext（兼容 approval_instance 无法 ALTER 的场景）。
 */
@Data
@TableName("approval_instance_ext")
public class ApprovalInstanceExt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long instanceId;
    private Long flowId;
    private String currentNodeNo;
    private Integer currentOrderNo;
    /** 当前节点指定审批人（转交后覆盖；空则按角色匹配） */
    private Long assigneeId;
    /** 加签审批人 id，逗号分隔 */
    private String extraApproverIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
