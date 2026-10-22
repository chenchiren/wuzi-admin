package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入批次业务主键关联：记录一次导入创建的业务数据，支持真回滚。
 * biz_table 取值：material / stock / stock_record / supplier / supplier_product。
 * ref_id 为关联主键（如 stock_record 关联的 stock.id）。
 */
@Data
@TableName("import_batch_biz")
public class ImportBatchBiz {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String dataType;
    private String bizTable;
    private Long bizId;
    private Long refId;
    private Integer seq;
    private LocalDateTime createdAt;
}
