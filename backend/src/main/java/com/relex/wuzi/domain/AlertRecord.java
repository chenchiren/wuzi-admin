package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预警记录：临期 / 低库存 / 超库存。
 * alert_type: EXPIRY / LOW_STOCK / OVER_STOCK
 * status: OPEN / READ / HANDLED
 */
@Data
@TableName("alert_record")
public class AlertRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertType;
    private String alertLevel;
    private String title;
    private String content;
    private Long materialId;
    private Long warehouseId;
    private String batchNo;
    private BigDecimal qty;
    private BigDecimal thresholdVal;
    private LocalDate expireDate;
    private String bizNo;
    private String status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
