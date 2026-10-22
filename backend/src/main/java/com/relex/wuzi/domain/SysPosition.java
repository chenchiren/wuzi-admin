package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 组织职位（岗位）：部长 / 副部长 / 主管等。
 * 与角色分离——角色管权限，职位管组织职务与审批身份。
 */
@Data
@TableName("sys_position")
public class SysPosition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String positionCode;
    private String positionName;
    /** 职级，数值越大越高：部长 80 > 副部长 70 */
    private Integer positionLevel;
    /** 是否领导岗（部门负责人从领导岗中取最高职级） */
    private Integer isLeader;
    private Integer sortNo;
    private Integer status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
