package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 盘点单。表 inventory_check 已建（见 sql/wuzi_full.sql）。
 */
@Data
@TableName("inventory_check")
public class InventoryCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String checkNo;
    private Long warehouseId;
    private Long operatorId;
    private LocalDateTime checkTime;
    private String status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
