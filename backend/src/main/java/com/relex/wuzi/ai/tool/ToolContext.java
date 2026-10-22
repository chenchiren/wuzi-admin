package com.relex.wuzi.ai.tool;

import java.util.List;

/**
 * 工具执行上下文：数据只来自 JWT（JwtAuthFilter 注入的 request attributes），
 * 不提供任何提权构造入口 —— 工具始终以当前登录用户身份执行。
 */
public record ToolContext(Long userId, String username, List<String> roles) {

    public boolean hasRole(String code) {
        return roles != null && (roles.contains(code) || roles.contains("ADMIN"));
    }
}
