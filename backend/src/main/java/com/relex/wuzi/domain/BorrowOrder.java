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
 * 借用单主表。
 * <p>
 * 说明：borrow_order 表已建（见 sql/wuzi_full.sql，源自 patch_missing_tables）。
 * status: DRAFT / APPROVED / LENT / PART_RETURNED / RETURNED / OVERDUE / CLOSED
 */
@Data
@TableName("borrow_order")
public class BorrowOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String borrowNo;
    private Long applyUserId;
    private Long deptId;
    private String borrowType;
    private String projectName;
    private LocalDate borrowDate;
    private LocalDate expectReturnDate;
    private LocalDate actualReturnDate;
    private String purpose;
    private String status;
    private String remark;
    private Integer isDeleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<BorrowOrderItem> items;

    @TableField(exist = false)
    private String approveComment;
}
