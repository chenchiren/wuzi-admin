package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trace_qr_print_log")
public class TraceQrPrintLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long qrId;
    private String qrCode;
    private String printBatchNo;
    private String printType;
    private String reprintReason;
    private String templateCode;
    private String templateSize;
    private String printerName;
    private Integer copyCount;
    private Long operatorId;
    private LocalDateTime printTime;
    private String bizType;
    private Long bizId;
}
