package com.relex.wuzi.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能助手消息。role: user / assistant / tool。
 * tool 消息仅落 ai_tool_log 摘要，本表主要存 user/assistant 正文。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String toolName;
    private String toolArgs;
    private Integer tokens;
    private LocalDateTime createdAt;
}
