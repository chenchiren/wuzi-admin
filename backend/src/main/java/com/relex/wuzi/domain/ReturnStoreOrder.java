package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退库单主表。状态机：APPLIED(申请) -> ACCEPTED(仓管验收) -> CONFIRMED(确认入库)
 * 可分支：REJECTED / CANCELLED / DRAFT
 */
@Data
@TableName("return_store_order")
public class ReturnStoreOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String returnNo;
    private Long applyId;
    private Long warehouseId;
    private Long applyUserId;
    private Long acceptUserId;
    private Long confirmUserId;
    private String status;
    private String applyRemark;
    private String acceptRemark;
    private String confirmRemark;
    private LocalDateTime applyTime;
    private LocalDateTime acceptTime;
    private LocalDateTime confirmTime;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
