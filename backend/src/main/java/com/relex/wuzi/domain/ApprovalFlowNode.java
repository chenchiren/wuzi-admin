package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_flow_node")
public class ApprovalFlowNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private String nodeNo;
    private String nodeName;
    private Integer orderNo;
    /** 审批角色编码，如 FINANCE（approver_type=ROLE 时生效） */
    private String approverRole;
    /** 指定审批人（优先于其他类型） */
    private Long assigneeId;
    /** 审批人类型：USER / ROLE / POSITION / DEPT_LEADER，默认 ROLE 兼容旧数据 */
    private String approverType;
    /** 职位编码，如 DEPT_HEAD / DEPT_VICE（approver_type=POSITION 时生效） */
    private String positionCode;
    /** 部门范围：ALL 不限 / SELF 申请人本部门 / PARENT 上级部门 */
    private String deptScope;
    private Integer isDeleted;
    private LocalDateTime createdAt;
}
