package com.relex.wuzi.ai.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.relex.wuzi.ai.domain.AiChatMessage;
import com.relex.wuzi.ai.domain.AiChatSession;
import com.relex.wuzi.ai.llm.LlmProperties;
import com.relex.wuzi.ai.service.AiCircuitBreaker;
import com.relex.wuzi.ai.service.AiChatService;
import com.relex.wuzi.ai.service.AiRateLimiter;
import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.common.PageResult;
import com.relex.wuzi.common.R;
import com.relex.wuzi.mapper.AiChatMessageMapper;
import com.relex.wuzi.mapper.AiChatSessionMapper;
import com.relex.wuzi.security.JwtAuthFilter;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 智能助手 /api/ai/**。SSE 流式对话 + 本人会话管理。
 * 安全：JWT 必登录（JwtAuthFilter）；路径规则在 ApiAccessRules 登记；
 * 写端点显式 @RequirePerm("ai:chat")（满足 PermContractChecker 门禁）；
 * 工具级权限由 ToolRegistry 按角色裁剪。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService chatService;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final LlmProperties props;
    private final AiRateLimiter rateLimiter;
    private final AiCircuitBreaker breaker;

    @Resource(name = "aiSseExecutor")
    private ExecutorService aiSseExecutor;

    @Data
    public static class AiChatReq {
        private Long sessionId;
        private String message;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePerm("ai:chat")
    public SseEmitter chat(@RequestBody AiChatReq req, HttpServletRequest request) {
        requireEnabled();
        if (req.getMessage() == null || req.getMessage().isBlank()) {
            throw new BizException("消息不能为空");
        }
        if (!breaker.allow()) {
            throw new BizException(503, "智能助手暂时不可用，请稍后再试");
        }
        Long userId = JwtAuthFilter.userId(request);
        List<String> roles = JwtAuthFilter.roles(request);
        String username = (String) request.getAttribute("username");
        // 限流与并发占用在 Tomcat 线程同步做：失败直接 JSON 错误，而不是半截 SSE
        rateLimiter.check(userId);
        rateLimiter.acquireSlot(userId);

        SseEmitter emitter = new SseEmitter(90_000L);
        boolean submitted = false;
        try {
            aiSseExecutor.submit(() -> {
                try {
                    chatService.chat(userId, username, roles, req.getSessionId(),
                            req.getMessage().trim(), emitter);
                    breaker.onSuccess();
                } catch (Exception e) {
                    log.error("AI chat task error, userId={}", userId, e);
                    breaker.onFailure();
                    chatService.sendError(emitter, "智能助手暂时不可用，请稍后再试（业务功能不受影响）");
                } finally {
                    rateLimiter.releaseSlot(userId);
                    emitter.complete();
                }
            });
            submitted = true;
            return emitter;
        } finally {
            if (!submitted) {
                rateLimiter.releaseSlot(userId);
            }
        }
    }

    @GetMapping("/sessions")
    @RequirePerm("ai:chat")
    public R<PageResult<AiChatSession>> sessions(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 HttpServletRequest request) {
        requireEnabled();
        LambdaQueryWrapper<AiChatSession> qw = new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getUserId, JwtAuthFilter.userId(request))
                .eq(AiChatSession::getIsDeleted, 0)
                .orderByDesc(AiChatSession::getUpdatedAt);
        Page<AiChatSession> p = sessionMapper.selectPage(new Page<>(page, size), qw);
        return R.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/sessions/{id}/messages")
    @RequirePerm("ai:chat")
    public R<List<AiChatMessage>> messages(@PathVariable Long id, HttpServletRequest request) {
        requireEnabled();
        AiChatSession s = requireOwned(id, request);
        List<AiChatMessage> list = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, s.getId())
                .orderByAsc(AiChatMessage::getId));
        return R.ok(list);
    }

    @DeleteMapping("/sessions/{id}")
    @RequirePerm("ai:chat")
    public R<Void> deleteSession(@PathVariable Long id, HttpServletRequest request) {
        requireEnabled();
        AiChatSession s = requireOwned(id, request);
        s.setIsDeleted(1);
        sessionMapper.updateById(s);
        return R.ok();
    }

    /** 助手可用性探测：前端据此决定悬浮球显隐 */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(Map.of(
                "enabled", props.isEnabled(),
                "degraded", !breaker.allow()));
    }

    // ---------------------------------------------------------------- private

    private void requireEnabled() {
        if (!props.isEnabled()) {
            throw new BizException(404, "智能助手未启用");
        }
    }

    private AiChatSession requireOwned(Long id, HttpServletRequest request) {
        AiChatSession s = sessionMapper.selectById(id);
        if (s == null || (s.getIsDeleted() != null && s.getIsDeleted() == 1)
                || !JwtAuthFilter.userId(request).equals(s.getUserId())) {
            throw new BizException("会话不存在");
        }
        return s;
    }
}
