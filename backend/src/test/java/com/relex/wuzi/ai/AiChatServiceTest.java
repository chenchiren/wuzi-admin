package com.relex.wuzi.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relex.wuzi.ai.domain.AiChatMessage;
import com.relex.wuzi.ai.llm.LlmClient;
import com.relex.wuzi.ai.llm.LlmProperties;
import com.relex.wuzi.ai.llm.LlmToolCall;
import com.relex.wuzi.ai.service.AiChatService;
import com.relex.wuzi.ai.service.AiPromptService;
import com.relex.wuzi.ai.tool.ToolContext;
import com.relex.wuzi.ai.tool.ToolRegistry;
import com.relex.wuzi.ai.tool.ToolResult;
import com.relex.wuzi.mapper.AiChatMessageMapper;
import com.relex.wuzi.mapper.AiChatSessionMapper;
import com.relex.wuzi.mapper.AiToolLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话编排单测（Mock LlmClient / Registry / Mapper，不起 Spring 上下文）。
 */
class AiChatServiceTest {

    private LlmClient llm;
    private ToolRegistry registry;
    private AiChatMessageMapper messageMapper;
    private AiToolLogMapper toolLogMapper;
    private AiChatService service;
    private SseEmitter emitter;

    @BeforeEach
    void setUp() {
        llm = mock(LlmClient.class);
        registry = mock(ToolRegistry.class);
        messageMapper = mock(AiChatMessageMapper.class);
        AiToolLogMapper toolLogMapperMock = mock(AiToolLogMapper.class);
        toolLogMapper = toolLogMapperMock;
        LlmProperties props = new LlmProperties();
        props.setBaseUrl("http://localhost:1/v1");
        props.setModel("test");
        props.setToolMaxRounds(4);
        props.setHistoryRounds(8);

        service = new AiChatService(llm, props, new AiPromptService(), registry,
                mock(AiChatSessionMapper.class), messageMapper, toolLogMapperMock,
                new ObjectMapper());
        emitter = new SseEmitter();
        // 历史消息查询统一返回空列表（真实 MyBatis-Plus 不会返回 null，mock 需显式打桩）
        when(messageMapper.selectList(any())).thenReturn(List.of());
    }

    private ToolContext ctx() {
        return new ToolContext(1L, "admin", List.of("ADMIN"));
    }

    @Test
    void directAnswerInsertsUserAndAssistant() {
        when(llm.streamChat(anyList(), anyList(), any())).thenAnswer(inv -> {
            java.util.function.Consumer<String> onDelta = inv.getArgument(2);
            onDelta.accept("你好");
            return new LlmClient.StreamResult(List.of(), 10, 2);
        });

        service.chat(1L, "admin", List.of("ADMIN"), null, "你好", emitter);

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        List<AiChatMessage> msgs = captor.getAllValues();
        assertEquals("user", msgs.get(0).getRole());
        assertEquals("assistant", msgs.get(1).getRole());
        assertEquals("你好", msgs.get(1).getContent());
        verify(toolLogMapper, times(0)).insert(any());
    }

    @Test
    void toolCallExecutesAndAnswers() throws Exception {
        var tool = mock(com.relex.wuzi.ai.tool.AiTool.class);
        when(tool.allowedFor(any())).thenReturn(true);
        when(tool.execute(any(), any())).thenReturn(
                ToolResult.of("共 1 条", List.of(Map.of("qty", 42)), "1行"));
        when(registry.byName("query_stock")).thenReturn(tool);
        when(registry.labelOf("query_stock")).thenReturn("查询库存");
        when(registry.visibleDefs(any())).thenReturn(List.of());

        when(llm.streamChat(anyList(), anyList(), any()))
                .thenReturn(new LlmClient.StreamResult(
                        List.of(new LlmToolCall("c1", "query_stock", "{\"materialName\":\"A4纸\"}")), null, null))
                .thenReturn(new LlmClient.StreamResult(List.of(), 20, 10));

        service.chat(1L, "admin", List.of("ADMIN"), null, "1号仓A4纸还剩多少", emitter);

        verify(tool, times(1)).execute(any(), any(ToolContext.class));
        verify(toolLogMapper, times(1)).insert(any());
        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        assertEquals("assistant", captor.getAllValues().get(1).getRole());
    }

    @Test
    void unknownToolDoesNotBreakFlow() {
        when(registry.byName("nope")).thenReturn(null);
        when(registry.labelOf("nope")).thenReturn("nope");
        when(registry.visibleDefs(any())).thenReturn(List.of());
        when(llm.streamChat(anyList(), anyList(), any()))
                .thenReturn(new LlmClient.StreamResult(
                        List.of(new LlmToolCall("c1", "nope", "{}")), null, null))
                .thenReturn(new LlmClient.StreamResult(List.of(), 5, 5));

        service.chat(1L, "admin", List.of("ADMIN"), null, "奇怪的问题", emitter);

        verify(toolLogMapper, times(1)).insert(any());
        verify(messageMapper, times(2)).insert(any(AiChatMessage.class));
    }

    @Test
    void toolRoundsExhaustedStops() {
        var tool = mock(com.relex.wuzi.ai.tool.AiTool.class);
        when(registry.byName("loop")).thenReturn(tool);
        when(registry.labelOf("loop")).thenReturn("循环");
        when(registry.visibleDefs(any())).thenReturn(List.of());
        when(llm.streamChat(anyList(), anyList(), any()))
                .thenAnswer(inv -> new LlmClient.StreamResult(
                        List.of(new LlmToolCall("c", "loop", "{}")), null, null));

        service.chat(1L, "admin", List.of("ADMIN"), null, "死循环问题", emitter);

        verify(llm, times(4)).streamChat(anyList(), anyList(), any());
        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        assertTrue(captor.getAllValues().get(1).getContent().contains("已终止"));
    }

    @Test
    void invalidArgsFeedBackToModel() throws Exception {
        var tool = mock(com.relex.wuzi.ai.tool.AiTool.class);
        when(tool.allowedFor(any())).thenReturn(true);
        when(tool.execute(any(), any())).thenThrow(new RuntimeException("物资不存在"));
        when(registry.byName("query_stock")).thenReturn(tool);
        when(registry.labelOf("query_stock")).thenReturn("查询库存");
        when(registry.visibleDefs(any())).thenReturn(List.of());
        when(llm.streamChat(anyList(), anyList(), any()))
                .thenReturn(new LlmClient.StreamResult(
                        List.of(new LlmToolCall("c1", "query_stock", "{\"materialName\":\"不存在\"}")), null, null))
                .thenReturn(new LlmClient.StreamResult(List.of(), 5, 5));

        service.chat(1L, "admin", List.of("ADMIN"), null, "查库存", emitter);

        verify(tool, times(1)).execute(any(), any());
        ArgumentCaptor<com.relex.wuzi.ai.domain.AiToolLog> logCaptor =
                ArgumentCaptor.forClass(com.relex.wuzi.ai.domain.AiToolLog.class);
        verify(toolLogMapper).insert(logCaptor.capture());
        assertEquals(0, logCaptor.getValue().getSuccess());
        verify(messageMapper, times(2)).insert(any(AiChatMessage.class));
    }

    @Test
    void sessionBelongingChecked() {
        var sessionMapper = mock(AiChatSessionMapper.class);
        LlmProperties props = new LlmProperties();
        AiChatService svc = new AiChatService(llm, props, new AiPromptService(), registry,
                sessionMapper, messageMapper, toolLogMapper, new ObjectMapper());

        com.relex.wuzi.ai.domain.AiChatSession others = new com.relex.wuzi.ai.domain.AiChatSession();
        others.setUserId(99L);
        others.setIsDeleted(0);
        when(sessionMapper.selectById(7L)).thenReturn(others);

        try {
            svc.chat(1L, "admin", List.of("ADMIN"), 7L, "你好", emitter);
        } catch (Exception expected) {
            // BizException 由 chat 内抛出（resolveOrCreateSession 在 try 外）→ 允许
        }
        verify(llm, times(0)).streamChat(anyList(), anyList(), any());
    }

    @Test
    void promptMentionsBusinessInvariants() {
        String prompt = new AiPromptService().systemPrompt();
        assertTrue(prompt.contains("amount_sign"));
        assertTrue(prompt.contains("enable_thinking") == false);
        assertTrue(prompt.contains("指令"));
    }
}
