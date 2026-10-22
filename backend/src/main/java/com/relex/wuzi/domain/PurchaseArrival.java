package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("purchase_arrival")
public class PurchaseArrival {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String arrivalNo;
    private Long orderId;
    private Long supplierId;
    /** 来源订单/供应商单号（非表字段，列表展示用） */
    @TableField(exist = false)
    private String orderNo;
    @TableField(exist = false)
    private String supplierName;
    private LocalDateTime arrivalTime;
    private Long operatorId;
    private String transportInfo;
    private String status;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
