package com.relex.wuzi.ai.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断：连续 3 次 LLM 失败 → 冷却 60s；冷却期内 /chat 快速降级、/status 显示 degraded。
 * 成功一次即清零计数。
 */
@Service
public class AiCircuitBreaker {

    private static final int THRESHOLD = 3;
    private static final long COOLDOWN_MS = 60_000L;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openUntil = 0L;

    public boolean allow() {
        return System.currentTimeMillis() >= openUntil;
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
    }

    public void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= THRESHOLD) {
            openUntil = System.currentTimeMillis() + COOLDOWN_MS;
        }
    }
}
