package com.relex.wuzi.security;

import com.relex.wuzi.common.BizException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权：非公开路径必须携带有效 Bearer Token，否则 401。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        // CORS 预检不带 Authorization，必须放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            write401(response);
            return;
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            request.setAttribute("username", claims.get("username", String.class));
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            request.setAttribute("roles", roles == null ? List.of() : roles);
        } catch (Exception e) {
            write401(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void write401(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"未登录或令牌失效\"}");
    }

    public static Long userId(HttpServletRequest request) {
        Object id = request.getAttribute("userId");
        if (id == null) {
            throw new BizException(401, "未登录");
        }
        return (Long) id;
    }

    @SuppressWarnings("unchecked")
    public static List<String> roles(HttpServletRequest request) {
        Object roles = request.getAttribute("roles");
        return roles == null ? List.of() : (List<String>) roles;
    }

    public static boolean isPublic(String path) {
        return path.equals("/error")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/m/auth/login")
                || path.startsWith("/api/m/wecom/")
                || path.startsWith("/api/m/auth/wechat-work/")
                || path.startsWith("/api/m/dingtalk/")
                || path.startsWith("/api/m/auth/dingtalk/")
                // 对外免登录公开溯源页（PC PublicTrace / 手机 Trace 共用）
                || path.startsWith("/api/trace/public")
                || path.startsWith("/api/price/calc")
                || path.startsWith("/api/import/templates")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/actuator/health");
    }
}
