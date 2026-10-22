package com.relex.wuzi.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attachment")
public class Attachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private String fileName;
    private String originName;
    private String filePath;
    private Long fileSize;
    private String contentType;
    private String md5;
    private Long uploadedBy;
    private Integer isDeleted;
    private LocalDateTime createdAt;
}
