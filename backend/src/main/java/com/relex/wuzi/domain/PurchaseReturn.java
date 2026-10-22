package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("purchase_return")
public class PurchaseReturn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String returnNo;
    private Long orderId;
    private Long supplierId;
    private String reason;
    private String status;
    private Integer amountSign;
    private BigDecimal taxInclAmount;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
