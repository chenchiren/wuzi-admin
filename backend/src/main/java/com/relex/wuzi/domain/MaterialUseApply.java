package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 领用申请主表（material_use_apply）
 * status: DRAFT / SUBMITTED / APPROVED / REJECTED / ISSUED / CLOSED
 */
@Data
@TableName("material_use_apply")
public class MaterialUseApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String applyNo;
    private Long applyUserId;
    private Long deptId;
    private String useType;
    private String projectName;
    private LocalDate needDate;
    private String purpose;
    private String status;
    /** 发料仓库（发料时回写，便于列表展示扣了哪个仓） */
    private Long issueWarehouseId;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<MaterialUseApplyItem> items;

    /** 审批意见（不落库，仅接口传参） */
    @TableField(exist = false)
    private String approveComment;

    /** 退库/发料附注（简化实现：仅写入 stock_record.remark） */
    @TableField(exist = false)
    private String flowRemark;
}
