package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物资别名词典：alias 全局唯一（一个别名只指向一个物资，防歧义）。
 * 智能助手解析用户口语化名称（"复印纸"→"A4打印纸"）的受控词典。
 */
@Data
@TableName("material_alias")
public class MaterialAlias {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private String alias;
    private LocalDateTime createdAt;
}
