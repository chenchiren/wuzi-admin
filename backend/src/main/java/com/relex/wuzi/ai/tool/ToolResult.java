package com.relex.wuzi.ai.tool;

import java.util.List;
import java.util.Map;

/**
 * 工具执行结果：rows 交给前端渲染迷你表格，summary 给模型作参考，
 * digest 存 ai_tool_log 审计。rows 必须已经过字段白名单与行数裁剪。
 */
public record ToolResult(String summary, List<Map<String, Object>> rows, String digest) {

    public static ToolResult of(String summary, List<Map<String, Object>> rows, String digest) {
        return new ToolResult(summary, rows == null ? List.of() : rows, digest);
    }
}
