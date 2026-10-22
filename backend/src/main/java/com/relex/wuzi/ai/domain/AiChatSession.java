package com.relex.wuzi.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能助手会话（本人所有，逻辑删除）。
 */
@Data
@TableName("ai_chat_session")
public class AiChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
