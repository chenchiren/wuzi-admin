package com.relex.wuzi.ai.service;

import com.relex.wuzi.ai.llm.LlmProperties;
import com.relex.wuzi.common.BizException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存限流：每用户 RPM 滑动窗口 + SSE 并发占位（无 Redis 现状下够用）。
 */
@Service
public class AiRateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final LlmProperties props;
    private final Map<Long, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> slots = new ConcurrentHashMap<>();

    public AiRateLimiter(LlmProperties props) {
        this.props = props;
    }

    /** 超限抛 BizException(429) */
    public void check(Long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> dq = windows.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && now - dq.peekFirst() > WINDOW_MS) {
                dq.pollFirst();
            }
            if (dq.size() >= Math.max(1, props.getRateLimitPerMinute())) {
                throw new BizException(429, "提问太频繁，请稍后再试");
            }
            dq.addLast(now);
        }
    }

    /** 同一用户同时只允许 1 路进行中的回答 */
    public void acquireSlot(Long userId) {
        AtomicInteger c = slots.computeIfAbsent(userId, k -> new AtomicInteger());
        if (c.incrementAndGet() > 1) {
            c.decrementAndGet();
            throw new BizException(429, "上一个回答还在进行中，请稍候");
        }
    }

    public void releaseSlot(Long userId) {
        AtomicInteger c = slots.get(userId);
        if (c != null) {
            c.updateAndGet(v -> Math.max(0, v - 1));
        }
    }
}
