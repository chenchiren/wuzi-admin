package com.relex.wuzi.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.ai.domain.AiChatMessage;
import com.relex.wuzi.ai.domain.AiChatSession;
import com.relex.wuzi.ai.domain.AiToolLog;
import com.relex.wuzi.ai.llm.LlmClient;
import com.relex.wuzi.ai.llm.LlmProperties;
import com.relex.wuzi.ai.llm.LlmToolCall;
import com.relex.wuzi.ai.tool.AiTool;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolRegistry;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.mapper.AiChatMessageMapper;
import com.relex.wuzi.mapper.AiChatSessionMapper;
import com.relex.wuzi.mapper.AiToolLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话编排：上下文组装 → LLM 流式 → 工具分发（≤tool-max-rounds 轮）→ 落库。
 * SSE 事件协议：delta / tool / tool_result / done / error。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String DEGRADED_MSG = "智能助手暂时不可用，请稍后再试（业务功能不受影响）";

    private final LlmClient llmClient;
    private final LlmProperties props;
    private final AiPromptService promptService;
    private final ToolRegistry toolRegistry;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiToolLogMapper toolLogMapper;
    private final ObjectMapper om;

    public void chat(Long userId, String username, List<String> roles,
                     Long sessionId, String message, SseEmitter emitter) {
        ToolContext ctx = new ToolContext(userId, username, roles);

        // 1. 会话：复用本人会话或新建（标题 = 首条消息前 20 字）
        AiChatSession session = resolveOrCreateSession(userId, sessionId, message);

        // 2. user 消息先落库（即使 LLM 挂了，提问可追溯）
        insertMessage(session.getId(), "user", message, null, null, null);

        // 3. 组装上下文：system + 最近 N 轮 user/assistant + 当前 user
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", promptService.systemPrompt()));
        appendHistory(session.getId(), messages);
        messages.add(Map.of("role", "user", "content", message));

        // 4. 按角色裁剪工具
        List<Map<String, Object>> tools = toolRegistry.visibleDefs(ctx);

        // 5. 编排循环
        Integer completionTokens = null;
        try {
            for (int round = 1; round <= Math.max(1, props.getToolMaxRounds()); round++) {
                StringBuilder answer = new StringBuilder();
                LlmClient.StreamResult result = llmClient.streamChat(messages, tools,
                        text -> {
                            answer.append(text);
                            send(emitter, "delta", Map.of("text", text));
                        });
                if (result.completionTokens() != null) {
                    completionTokens = result.completionTokens();
                }

                if (!result.hasToolCalls()) {
                    insertMessage(session.getId(), "assistant", answer.toString(), null, null, completionTokens);
                    send(emitter, "done", Map.of("sessionId", session.getId()));
                    return;
                }

                // assistant 工具调用消息（协议要求先回 assistant.tool_calls，再逐个回 tool）
                List<Map<String, Object>> toolCallJson = new ArrayList<>();
                for (LlmToolCall call : result.toolCalls()) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", call.name());
                    fn.put("arguments", call.argumentsJson());
                    Map<String, Object> tc = new LinkedHashMap<>();
                    tc.put("id", call.id());
                    tc.put("type", "function");
                    tc.put("function", fn);
                    toolCallJson.add(tc);
                }
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", answer.length() > 0 ? answer.toString() : null);
                assistantMsg.put("tool_calls", toolCallJson);
                messages.add(assistantMsg);

                // 执行工具
                for (LlmToolCall call : result.toolCalls()) {
                    executeAndFeedBack(call, ctx, session.getId(), emitter, messages);
                }
            }
            // 轮数用尽仍未收敛
            log.warn("AI tool rounds exhausted, userId={}, sessionId={}", userId, session.getId());
            sendError(emitter, "这个问题需要太多步查询，请拆成小问题再问");
            insertMessage(session.getId(), "assistant", "（问题较复杂，已终止，请拆分提问）", null, null, completionTokens);
        } catch (Exception e) {
            log.error("AI chat failed, userId={}, sessionId={}", userId, session.getId(), e);
            sendError(emitter, DEGRADED_MSG);
        }
    }

    /** 执行单个工具调用并把结果作为 tool 消息回传模型；审计落 ai_tool_log */
    private void executeAndFeedBack(LlmToolCall call, ToolContext ctx, Long sessionId,
                                    SseEmitter emitter, List<Map<String, Object>> messages) {
        String toolName = call.name();
        AiTool tool = toolRegistry.byName(toolName);
        send(emitter, "tool", Map.of("name", toolName, "label", toolRegistry.labelOf(toolName)));

        String resultContent;
        String digest = null;
        long start = System.currentTimeMillis();
        boolean ok = false;
        String errorMsg = null;

        if (tool == null) {
            errorMsg = "工具不存在: " + toolName;
            resultContent = "{\"error\":\"" + errorMsg + "\"}";
        } else if (!tool.allowedFor(ctx)) {
            errorMsg = "无权限调用: " + toolName;
            resultContent = "{\"error\":\"用户无权限使用该查询，请如实告知用户\"}";
        } else {
            Map<String, Object> args;
            try {
                args = toolRegistry.parseArgs(call.argumentsJson());
                toolRegistry.validateArgs(tool, args);
                ToolResult result = tool.execute(args, ctx);
                ok = true;
                digest = result.digest();
                resultContent = om.writeValueAsString(Map.of(
                        "summary", result.summary() == null ? "" : result.summary(),
                        "rows", result.rows()));
            } catch (Exception e) {
                errorMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                resultContent = "{\"error\":\"" + escape(errorMsg) + "\"}";
            }
        }

        long cost = System.currentTimeMillis() - start;
        insertToolLog(ctx.userId(), sessionId, toolName, call.argumentsJson(), digest, cost, ok, errorMsg);

        if (ok) {
            List<Map<String, Object>> rows = null;
            try {
                // 回传给模型的行已在 execute 内裁剪；此处仅解析用于 tool_result 事件
                var tree = om.readTree(resultContent);
                var rowsNode = tree.path("rows");
                rows = om.convertValue(rowsNode, List.class);
            } catch (Exception ignored) {
                // ignored
            }
            send(emitter, "tool_result", Map.of("name", toolName, "rows", rows == null ? List.of() : rows));
        } else {
            log.warn("AI tool failed: tool={}, user={}, err={}", toolName, ctx.userId(), errorMsg);
        }

        Map<String, Object> toolMsg = new LinkedHashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", call.id());
        toolMsg.put("content", resultContent);
        messages.add(toolMsg);
    }

    // ---------------------------------------------------------------- 会话与落库

    private AiChatSession resolveOrCreateSession(Long userId, Long sessionId, String firstMessage) {
        if (sessionId != null) {
            AiChatSession s = sessionMapper.selectById(sessionId);
            if (s == null || (s.getIsDeleted() != null && s.getIsDeleted() == 1)
                    || !userId.equals(s.getUserId())) {
                throw new BizException("会话不存在");
            }
            return s;
        }
        AiChatSession s = new AiChatSession();
        s.setUserId(userId);
        s.setTitle(abbreviate(firstMessage, 20));
        s.setIsDeleted(0);
        sessionMapper.insert(s);
        return s;
    }

    private void appendHistory(Long sessionId, List<Map<String, Object>> messages) {
        int limit = Math.max(0, props.getHistoryRounds()) * 2;
        if (limit == 0) {
            return;
        }
        List<AiChatMessage> history = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .in(AiChatMessage::getRole, "user", "assistant")
                .orderByDesc(AiChatMessage::getId)
                .last("LIMIT " + limit));
        for (int i = history.size() - 1; i >= 0; i--) {
            AiChatMessage m = history.get(i);
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            messages.add(Map.of("role", m.getRole(), "content", m.getContent()));
        }
    }

    private void insertMessage(Long sessionId, String role, String content,
                               String toolName, String toolArgs, Integer tokens) {
        AiChatMessage m = new AiChatMessage();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setToolName(toolName);
        m.setToolArgs(toolArgs);
        m.setTokens(tokens);
        messageMapper.insert(m);
    }

    private void insertToolLog(Long userId, Long sessionId, String toolName, String argsJson,
                               String digest, long costMs, boolean ok, String errorMsg) {
        try {
            AiToolLog l = new AiToolLog();
            l.setUserId(userId);
            l.setSessionId(sessionId);
            l.setToolName(toolName);
            l.setArgsJson(truncate(argsJson, 2000));
            l.setResultDigest(truncate(digest, 500));
            l.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
            l.setSuccess(ok ? 1 : 0);
            l.setErrorMsg(truncate(errorMsg, 500));
            toolLogMapper.insert(l);
        } catch (Exception e) {
            log.warn("ai_tool_log insert failed: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- SSE 输出

    public void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(om.writeValueAsString(data)));
        } catch (Exception e) {
            // 客户端断开等场景，静默
        }
    }

    public void sendError(SseEmitter emitter, String msg) {
        send(emitter, "error", Map.of("msg", msg));
    }

    // ---------------------------------------------------------------- 工具方法

    private static String abbreviate(String s, int max) {
        if (s == null) {
            return "新对话";
        }
        String trimmed = s.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
