package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 标签模板（库表）：尺寸 / DPI / 显示字段开关 / 可选自定义 HTML。
 * 与 service.LabelTemplate（yml 默认配置）互补：库表优先，配置兜底。
 */
@Data
@TableName("label_template")
public class LabelTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateCode;
    private String templateName;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private Integer dpi;
    private String title;
    /** 二维码模块大小（ZPL ^BQN 倍数 2-10） */
    private Integer qrModuleSize;
    private Integer showBatch;
    private Integer showSerial;
    private Integer showSupplier;
    private Integer showPrice;
    /** 可选自定义 HTML 标签体；空则用内置布局 */
    private String htmlBody;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
