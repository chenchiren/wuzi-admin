package com.relex.wuzi.web.dto;

import lombok.Data;

import java.util.List;

/** 创建入库单命令 */
@Data
public class InboundCreateCmd {
    private Long purchaseOrderId;
    private Long warehouseId;
    private Long supplierId;
    /** 默认库位，明细未指定时写入 */
    private Long locationId;
    private String remark;
    private List<InboundCreateItem> items;
}
