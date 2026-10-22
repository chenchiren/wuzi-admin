package com.relex.wuzi.security;

import java.util.List;
import java.util.Map;

/**
 * 路径 → 按钮权限码映射（兼容层）。
 * <p>
 * 新代码请在 Controller 方法上使用 {@code @RequirePerm("资源:动作")} 显式声明。
 * 本类不再用 {@code endsWith} 启发式猜测动作；仅在写接口缺少注解且路径命中
 * 显式注册表时兜底（并由 {@link PermContractChecker} 在启动期禁止无注解写接口）。
 * </p>
 */
public final class PermCodeResolver {

    private PermCodeResolver() {
    }

    /** 前缀 → 权限资源 */
    private static final Map<String, String> WRITE_BASE = Map.ofEntries(
            Map.entry("/api/users", "sys:user"),
            Map.entry("/api/roles", "sys:role"),
            Map.entry("/api/depts", "sys:dept"),
            Map.entry("/api/positions", "sys:position"),
            Map.entry("/api/permissions", "sys:perm"),
            Map.entry("/api/materials", "base:material"),
            Map.entry("/api/categories", "base:category"),
            Map.entry("/api/suppliers", "base:supplier"),
            Map.entry("/api/supplier-products", "base:supplier-product"),
            Map.entry("/api/supplier-contacts", "base:supplier-contact"),
            Map.entry("/api/tax-rates", "base:tax-rate"),
            Map.entry("/api/purchase/applies", "po:apply"),
            Map.entry("/api/purchase/inquiries", "po:inquiry"),
            Map.entry("/api/purchase/orders", "po:order"),
            Map.entry("/api/purchase/returns", "po:return"),
            Map.entry("/api/purchase/arrivals", "po:arrival"),
            Map.entry("/api/invoices", "fin:invoice"),
            Map.entry("/api/settles", "fin:settle"),
            Map.entry("/api/purchase-plans", "po:plan"),
            Map.entry("/api/budgets", "fin:budget"),
            Map.entry("/api/contracts", "po:contract"),
            Map.entry("/api/payments", "fin:payment"),
            Map.entry("/api/warehouses", "wh:warehouse"),
            Map.entry("/api/inbound", "wh:inbound"),
            Map.entry("/api/outbound", "wh:outbound"),
            Map.entry("/api/transfer", "wh:transfer"),
            Map.entry("/api/inventory", "wh:inventory"),
            Map.entry("/api/material-use", "asset:use"),
            Map.entry("/api/return-stores", "asset:return"),
            Map.entry("/api/borrows", "asset:borrow"),
            Map.entry("/api/scraps", "asset:scrap"),
            Map.entry("/api/trace", "trace:qr"),
            Map.entry("/api/labels", "label"),
            Map.entry("/api/label-templates", "label:template"),
            Map.entry("/api/alerts", "alert"),
            Map.entry("/api/messages", "msg"),
            Map.entry("/api/ai", "ai"),
            Map.entry("/api/import", "sys:import"),
            Map.entry("/api/configs", "sys:config"),
            Map.entry("/api/flow-approvals/flows", "flow:config"),
            Map.entry("/api/flow-approvals", "approval"),
            Map.entry("/api/approvals", "approval"),
            Map.entry("/api/red-flush", "wh:outbound")
    );

    /**
     * 解析写操作所需权限码；返回 null 表示不强制按钮权限
     * （仍受角色规则约束；正常应由 {@code @RequirePerm} 覆盖）。
     * <p>已移除 endsWith/contains 启发式，仅支持完整路径注册表 + 显式动作表。</p>
     */
    public static String resolve(String method, String path) {
        if (!ApiAccessRules.isWrite(method)) {
            return null;
        }
        String explicit = EXPLICIT_PATHS.get(path);
        if (explicit != null) {
            return explicit;
        }
        String base = baseOf(path);
        if (base == null) {
            return null;
        }
        // 仅按 HTTP 动词归约，不再猜测 /submit /approve 等后缀
        if ("DELETE".equalsIgnoreCase(method)) {
            return base + ":delete";
        }
        if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
            return base + ":update";
        }
        return base + ":create";
    }

    /** 完整路径（无路径变量的稳定端点）→ 权限码；带变量的请用 @RequirePerm */
    private static final Map<String, String> EXPLICIT_PATHS = Map.ofEntries(
            Map.entry("/api/flow-approvals/flows", "flow:config:create"),
            Map.entry("/api/label-templates/preview", "label:template:preview"),
            Map.entry("/api/messages/read-all", "msg:read"),
            Map.entry("/api/import/templates", "sys:import:template")
    );

    private static String baseOf(String path) {
        String best = null;
        for (Map.Entry<String, String> e : WRITE_BASE.entrySet()) {
            if (path.startsWith(e.getKey()) && (best == null || e.getKey().length() > best.length())) {
                best = e.getKey();
            }
        }
        return best == null ? null : WRITE_BASE.get(best);
    }

    public static List<String> allKnownBases() {
        return WRITE_BASE.values().stream().distinct().sorted().toList();
    }
}
