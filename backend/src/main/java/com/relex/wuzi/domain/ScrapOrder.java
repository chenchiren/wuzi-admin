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
 * 报废单主表。
 * <p>
 * 说明：scrap_order 表已建（见 sql/wuzi_full.sql，源自 patch_missing_tables）。
 * status: DRAFT / SUBMITTED / CONFIRMED / REJECTED
 */
@Data
@TableName("scrap_order")
public class ScrapOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String scrapNo;
    private Long applyUserId;
    private Long deptId;
    private String scrapReason;
    private LocalDate scrapDate;
    private String status;
    private String remark;
    private Integer isDeleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<ScrapOrderItem> items;

    @TableField(exist = false)
    private String confirmComment;
}
