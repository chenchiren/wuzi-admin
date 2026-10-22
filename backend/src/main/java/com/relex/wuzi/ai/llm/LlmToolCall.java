package com.relex.wuzi.ai.llm;

/**
 * 模型的一次工具调用请求（OpenAI 协议 tool_calls 项）。
 * argumentsJson 为分片拼接后的完整 JSON 串，可能仍非法（由 ToolRegistry 校验）。
 */
public record LlmToolCall(String id, String name, String argumentsJson) {
}
