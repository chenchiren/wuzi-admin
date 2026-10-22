package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("warehouse_location")
public class WarehouseLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long warehouseId;
    private String locationCode;
    private String locationName;
    private String area;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
}
