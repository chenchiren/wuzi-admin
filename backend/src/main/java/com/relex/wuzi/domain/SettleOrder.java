package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("settle_order")
public class SettleOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String settleNo;
    private Long orderId;
    private Long invoiceId;
    private Long supplierId;
    private LocalDate settleDate;
    private BigDecimal settleAmount;
    private BigDecimal taxAmount;
    private String status;
    private Long deptId;
    private String remark;
    private Integer isDeleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
