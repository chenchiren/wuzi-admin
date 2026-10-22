package com.relex.wuzi.security;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 路径级 RBAC：系统管理仅 ADMIN；业务写操作按角色白名单放行。
 * <p>策略：<b>默认拒绝</b> —— 未登记的写接口不再默认放行，必须显式登记或在按钮权限层声明。</p>
 */
public final class ApiAccessRules {

    private ApiAccessRules() {
    }

    public static final Set<String> ADMIN_ONLY_PREFIX = Set.of(
            "/api/users",
            "/api/roles",
            "/api/depts",
            "/api/positions",
            "/api/permissions",
            "/api/logs",
            "/api/configs"
    );

    /** 系统管理写操作前缀 */
    public static final List<String> ADMIN_WRITE_PREFIX = List.of(
            "/api/tax-rates",
            "/api/import/"
    );

    public static final Map<String, List<String>> WRITE_ROLE_RULES = Map.ofEntries(
            // 采购申请：全员可发起；订单/询价/到货/退货偏采购
            Map.entry("/api/purchase/applies", List.of("ADMIN", "PURCHASER", "DEPT_MANAGER", "EMPLOYEE", "FINANCE")),
            Map.entry("/api/purchase/", List.of("ADMIN", "PURCHASER")),
            Map.entry("/api/suppliers", List.of("ADMIN", "PURCHASER")),
            Map.entry("/api/supplier-products", List.of("ADMIN", "PURCHASER")),
            Map.entry("/api/supplier-contacts", List.of("ADMIN", "PURCHASER")),
            Map.entry("/api/invoices", List.of("ADMIN", "FINANCE")),
            Map.entry("/api/settles", List.of("ADMIN", "FINANCE")),
            Map.entry("/api/warehouses", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/inbound/", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/outbound/", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/transfer/", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/inventory/", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/stock/", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/borrows", List.of("ADMIN", "WAREHOUSE", "DEPT_MANAGER", "EMPLOYEE")),
            Map.entry("/api/scraps", List.of("ADMIN", "WAREHOUSE", "DEPT_MANAGER")),
            Map.entry("/api/return-stores", List.of("ADMIN", "WAREHOUSE", "DEPT_MANAGER", "EMPLOYEE")),
            Map.entry("/api/material-use", List.of("ADMIN", "WAREHOUSE", "DEPT_MANAGER", "EMPLOYEE")),
            Map.entry("/api/alerts", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/trace/qr", List.of("ADMIN", "WAREHOUSE", "PURCHASER")),
            Map.entry("/api/labels", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/label-templates", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/approvals/", List.of("ADMIN", "DEPT_MANAGER", "FINANCE", "WAREHOUSE", "PURCHASER")),
            Map.entry("/api/flow-approvals/", List.of("ADMIN", "DEPT_MANAGER", "FINANCE", "WAREHOUSE", "PURCHASER")),
            Map.entry("/api/purchase-plans", List.of("ADMIN", "PURCHASER", "DEPT_MANAGER", "FINANCE")),
            Map.entry("/api/budgets", List.of("ADMIN", "FINANCE", "DEPT_MANAGER")),
            Map.entry("/api/contracts", List.of("ADMIN", "PURCHASER", "FINANCE")),
            Map.entry("/api/payments", List.of("ADMIN", "FINANCE")),
            Map.entry("/api/red-flush", List.of("ADMIN", "WAREHOUSE", "FINANCE")),
            Map.entry("/api/excel", List.of("ADMIN", "WAREHOUSE", "PURCHASER")),
            Map.entry("/api/dicts", List.of("ADMIN")),
            Map.entry("/api/sys-dict", List.of("ADMIN")),
            Map.entry("/api/basic", List.of("ADMIN", "PURCHASER", "WAREHOUSE")),
            Map.entry("/api/materials", List.of("ADMIN", "PURCHASER", "WAREHOUSE")),
            Map.entry("/api/categories", List.of("ADMIN", "PURCHASER")),
            Map.entry("/api/notify", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/supplier-eval", List.of("ADMIN", "PURCHASER", "FINANCE")),
            Map.entry("/api/stock-ledger", List.of("ADMIN", "WAREHOUSE", "FINANCE")),
            Map.entry("/api/reports", List.of("ADMIN", "FINANCE", "DEPT_MANAGER", "PURCHASER", "WAREHOUSE")),
            Map.entry("/api/stats", List.of("ADMIN", "FINANCE", "DEPT_MANAGER", "PURCHASER", "WAREHOUSE"))
    );

    /** 手机端写接口按作业域收紧（不再一把梭全角色） */
    public static final Map<String, List<String>> MOBILE_WRITE_RULES = Map.ofEntries(
            Map.entry("/api/m/inbounds", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/m/outbounds", List.of("ADMIN", "WAREHOUSE")),
            Map.entry("/api/m/arrivals", List.of("ADMIN", "WAREHOUSE", "PURCHASER")),
            Map.entry("/api/m/material-use", List.of("ADMIN", "WAREHOUSE", "EMPLOYEE", "DEPT_MANAGER")),
            Map.entry("/api/m/borrows", List.of("ADMIN", "WAREHOUSE", "EMPLOYEE", "DEPT_MANAGER")),
            Map.entry("/api/m/approvals", List.of("ADMIN", "DEPT_MANAGER", "FINANCE", "WAREHOUSE", "PURCHASER")),
            Map.entry("/api/m/scan", List.of("ADMIN", "WAREHOUSE", "EMPLOYEE", "DEPT_MANAGER", "PURCHASER", "FINANCE")),
            Map.entry("/api/m/sync", List.of("ADMIN", "WAREHOUSE", "EMPLOYEE", "DEPT_MANAGER", "PURCHASER", "FINANCE")),
            Map.entry("/api/m/messages", List.of("ADMIN", "WAREHOUSE", "EMPLOYEE", "DEPT_MANAGER", "PURCHASER", "FINANCE")),
            Map.entry("/api/m/me", List.of("ADMIN", "WAREHOUSE", "EMPLOYEE", "DEPT_MANAGER", "PURCHASER", "FINANCE"))
    );

    public static boolean isAdminOnly(String path) {
        if (ADMIN_ONLY_PREFIX.stream().anyMatch(path::startsWith)) {
            return true;
        }
        return path.startsWith("/api/flow-approvals/flows");
    }

    public static boolean isWrite(String method) {
        return !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method);
    }

    /**
     * 返回所需角色；null = 任意已登录用户。
     * 未登记的写接口返回 {@link #DENY}（默认拒绝）。
     */
    public static final List<String> DENY = List.of("__DENY__");

    public static List<String> requiredRoles(String method, String path) {
        if (!isWrite(method)) {
            return null;
        }
        if (ADMIN_ONLY_PREFIX.stream().anyMatch(path::startsWith)
                || ADMIN_WRITE_PREFIX.stream().anyMatch(path::startsWith)) {
            return List.of("ADMIN");
        }
        // 消息已读 / 附件 / 自助操作 / 智能助手会话：任意登录用户（按钮级由 @LoginOnly / @RequirePerm 约束）
        if (path.startsWith("/api/messages")
                || path.startsWith("/api/attachments")
                || path.startsWith("/api/ai")
                || path.startsWith("/api/m/messages")
                || path.startsWith("/api/m/scan")
                || path.startsWith("/api/m/sync")
                || path.startsWith("/api/m/me")) {
            return null;
        }
        // 手机作业：按端点域收口
        if (path.startsWith("/api/m/")) {
            List<String> mobile = matchPrefix(MOBILE_WRITE_RULES, path);
            return mobile != null ? mobile : DENY;
        }
        List<String> roles = matchPrefix(WRITE_ROLE_RULES, path);
        if (roles != null) {
            return roles;
        }
        // 默认拒绝：未登记写接口
        return DENY;
    }

    private static List<String> matchPrefix(Map<String, List<String>> rules, String path) {
        String best = null;
        for (Map.Entry<String, List<String>> e : rules.entrySet()) {
            if (path.startsWith(e.getKey()) && (best == null || e.getKey().length() > best.length())) {
                best = e.getKey();
            }
        }
        return best == null ? null : rules.get(best);
    }

    public static boolean allows(List<String> userRoles, List<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (required.contains("__DENY__")) {
            return false;
        }
        if (userRoles == null) {
            return false;
        }
        for (String r : required) {
            if (userRoles.contains(r)) {
                return true;
            }
        }
        return false;
    }
}
