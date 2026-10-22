package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trace_scan_log")
public class TraceScanLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long qrId;
    private String qrCode;
    private String scanType;
    private String bizType;
    private Long bizId;
    private Long operatorId;
    private String operatorName;
    private String channel;
    private Long warehouseId;
    private String clientIp;
    private String result;
    private String failReason;
    private LocalDateTime scanTime;
}
