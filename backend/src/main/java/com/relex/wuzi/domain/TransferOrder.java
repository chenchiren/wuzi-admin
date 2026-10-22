package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调拨单。表 transfer_order 已建（见 sql/wuzi_full.sql）。
 */
@Data
@TableName("transfer_order")
public class TransferOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String transferNo;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private Long operatorId;
    private LocalDateTime transferTime;
    private String status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
