package com.relex.wuzi.security;

import com.relex.wuzi.service.PermissionService;
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
 * 接口级 RBAC + 按钮级权限兜底：
 * 1) ADMIN 专属路径
 * 2) 写操作角色规则
 * 3) 路径解析出的权限码（PermCodeResolver）
 * 显式 @RequirePerm 由 PermAspect 再校验一次（更细）。
 */
@Component
@RequiredArgsConstructor
public class RoleAccessFilter extends OncePerRequestFilter {

    private final PermissionService permissionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (JwtAuthFilter.isPublic(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        Object uidAttr = request.getAttribute("userId");
        if (uidAttr == null) {
            write401(response);
            return;
        }
        Long userId = uidAttr instanceof Long l ? l : Long.valueOf(uidAttr.toString());
        List<String> roles = JwtAuthFilter.roles(request);
        String method = request.getMethod();

        if (ApiAccessRules.isAdminOnly(path) && !roles.contains(PermissionService.ADMIN)) {
            write403(response, "无权限：需要管理员");
            return;
        }
        List<String> required = ApiAccessRules.requiredRoles(method, path);
        if (!ApiAccessRules.allows(roles, required)) {
            write403(response, "无权限：当前角色不可执行该操作");
            return;
        }

        // 按钮级权限兜底（写操作）
        String permCode = PermCodeResolver.resolve(method, path);
        if (permCode != null && !permissionService.hasPerm(userId, roles, permCode, false)) {
            write403(response, "无权限：缺少操作权限 " + permCode);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void write401(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"未登录或令牌失效\"}");
    }

    private void write403(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"msg\":\"" + msg + "\"}");
    }
}
