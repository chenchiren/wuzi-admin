package com.relex.wuzi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路 ID：请求头 X-Request-Id 优先，否则生成 UUID，写入 MDC 并回写响应头。
 * 日志 pattern 可用 %X{requestId} 输出。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rid = request.getHeader(HEADER);
        if (rid == null || rid.isBlank() || rid.length() > 64) {
            rid = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_KEY, rid);
        response.setHeader(HEADER, rid);
        request.setAttribute(MDC_KEY, rid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String current() {
        String rid = MDC.get(MDC_KEY);
        return rid == null ? "" : rid;
    }
}
