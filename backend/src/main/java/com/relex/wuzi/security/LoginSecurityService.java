package com.relex.wuzi.security;

import com.relex.wuzi.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录安全：用户名+IP 内存限流 + 失败计数锁定。
 * 限流窗口内超过 max-failures 次失败则拒绝登录，直到窗口滑过或锁定到期。
 */
@Slf4j
@Service
public class LoginSecurityService {

    /** 窗口内最大失败次数（含） */
    @Value("${wuzi.auth.login.max-failures:10}")
    private int maxFailures;
    /** 统计窗口（分钟） */
    @Value("${wuzi.auth.login.window-minutes:5}")
    private int windowMinutes;
    /** 触发后锁定时长（分钟） */
    @Value("${wuzi.auth.login.lock-minutes:5}")
    private int lockMinutes;

    private final Map<String, FailWindow> failWindows = new ConcurrentHashMap<>();

    /** 登录前检查：超限则抛 BizException */
    public void checkAllowed(String username, String ip) {
        FailWindow w = failWindows.get(key(username, ip));
        if (w == null) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        if (w.lockedUntil > now) {
            long remainSec = (w.lockedUntil - now) / 1000;
            throw new BizException(429, "登录失败次数过多，请 " + remainSec + " 秒后重试");
        }
        long windowMs = windowMinutes * 60_000L;
        if (now - w.windowStart > windowMs) {
            // 窗口已过期，自动解除
            failWindows.remove(key(username, ip));
        }
    }

    /** 记录一次登录失败；达到阈值则锁定 */
    public void onFailure(String username, String ip) {
        long now = Instant.now().toEpochMilli();
        long windowMs = windowMinutes * 60_000L;
        FailWindow w = failWindows.compute(key(username, ip), (k, old) -> {
            FailWindow cur = old;
            if (cur == null || now - cur.windowStart > windowMs) {
                cur = new FailWindow();
                cur.windowStart = now;
            }
            cur.failCount++;
            if (cur.failCount >= Math.max(1, maxFailures)) {
                cur.lockedUntil = now + lockMinutes * 60_000L;
                log.warn("[auth] 登录失败次数超限 username={} ip={} count={} 锁定{}分钟",
                        username, ip, cur.failCount, lockMinutes);
            }
            return cur;
        });
        if (w != null && w.lockedUntil > now) {
            log.info("[auth] 账号临时锁定 username={} ip={} until={}", username, ip, w.lockedUntil);
        }
    }

    /** 登录成功：清除该用户+IP 的失败窗口 */
    public void onSuccess(String username, String ip) {
        failWindows.remove(key(username, ip));
    }

    private static String key(String username, String ip) {
        return (username == null ? "" : username.trim().toLowerCase()) + "|" + (ip == null ? "" : ip);
    }

    private static final class FailWindow {
        long windowStart;
        int failCount;
        long lockedUntil;
    }
}
