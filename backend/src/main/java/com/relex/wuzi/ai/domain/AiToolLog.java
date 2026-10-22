package com.relex.wuzi.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工具调用审计：一次工具执行一条（含失败）。
 */
@Data
@TableName("ai_tool_log")
public class AiToolLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long sessionId;
    private String toolName;
    private String argsJson;
    private String resultDigest;
    private Integer costMs;
    private Integer success;
    private String errorMsg;
    private LocalDateTime createdAt;
}
