package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("outbound_order")
public class OutboundOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String outboundNo;
    private String outboundType;
    private Long warehouseId;
    private Long applyId;
    private Long deptId;
    private Long userId;
    private Long operatorId;
    private LocalDateTime outboundTime;
    private String status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
