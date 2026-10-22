package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;

@Data
@TableName("trace_qr_code")
public class TraceQrCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String qrCode;
    private String qrType;
    private String qrContent;
    private String traceToken;
    private String contentMode;
    private Long materialId;
    private String batchNo;
    private Long batchId;
    private String serialNo;
    private Long serialId;
    private String materialCode;
    private String materialName;
    private String spec;
    private String unit;
    private Long supplierId;
    private Long purchaseOrderId;
    private Long inboundOrderId;
    private String status;
    private String qrGranularity;
    private Long currentWarehouseId;
    private Long currentLocationId;
    private Integer printCount;
    private Integer scanCount;
    private java.time.LocalDateTime firstScanAt;
    private java.time.LocalDateTime lastScanAt;
    private java.time.LocalDateTime activatedAt;
    private java.time.LocalDateTime retiredAt;
    private java.time.LocalDate expireDate;
    private Integer labelVersion;
    private Integer isEnabled;
    private Integer isDeleted;
    private Long createdBy;
    private java.time.LocalDateTime createdAt;
    private Long updatedBy;
    private java.time.LocalDateTime updatedAt;
}
