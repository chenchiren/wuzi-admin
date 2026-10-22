package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("material_category")
public class MaterialCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String categoryCode;
    private String categoryName;
    private Integer sortNo;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<MaterialCategory> children;
}
