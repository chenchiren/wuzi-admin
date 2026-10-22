package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目 / 成本中心主数据：project_type 区分 PROJECT 项目与 COST_CENTER 成本中心。
 */
@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectCode;
    private String projectName;
    /** PROJECT 项目 / COST_CENTER 成本中心 */
    private String projectType;
    private Long deptId;
    private String managerName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
