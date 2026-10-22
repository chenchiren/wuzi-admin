package com.relex.wuzi.ai.tool;

import java.util.Map;

/**
 * 智能助手工具 SPI。P0 全部只读；
 * allowedFor 返回 false 的工具不进 tools 定义（对模型不可见）。
 */
public interface AiTool {

    /** 工具名（= OpenAI function name），如 query_stock */
    String name();

    /** 前端展示用的中文短标签，如「查询库存」 */
    String label();

    /** 给模型的功能描述：做什么、什么时候用、不做什么 */
    String description();

    /** OpenAI function parameters（JSON Schema，Map 形态） */
    Map<String, Object> jsonSchema();

    /** 按用户角色裁剪可见性 */
    boolean allowedFor(ToolContext ctx);

    /** 执行查询并返回已裁剪的结果；抛异常视为本次调用失败（记审计、可回传模型重试一次） */
    ToolResult execute(Map<String, Object> args, ToolContext ctx) throws Exception;
}
