package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_serial")
public class StockSerial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String serialNo;
    private Long materialId;
    private Long warehouseId;
    private Long locationId;
    private String batchNo;
    private String status;
    private Long userId;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
