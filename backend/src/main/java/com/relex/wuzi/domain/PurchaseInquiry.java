package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("purchase_inquiry")
public class PurchaseInquiry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String inquiryNo;
    private Long applyId;
    private String title;
    private LocalDateTime inquireTime;
    private String status;
    private Long selectedSupplierId;
    /** 审批人 / 审批通过时间 */
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 来源申请单号（非表字段，列表展示用） */
    @TableField(exist = false)
    private String applyNo;
}
