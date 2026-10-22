package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("inbound_order")
public class InboundOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String inboundNo;
    private String inboundType;
    private Long warehouseId;
    private Long supplierId;
    private String sourceNo;
    private Long sourceId;
    private Long operatorId;
    private LocalDateTime inboundTime;
    private String status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
