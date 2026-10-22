package com.relex.wuzi.security;

import com.relex.wuzi.annotation.LoginOnly;
import com.relex.wuzi.annotation.RequirePerm;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 写接口权限契约校验：启动时强制每个业务写端点声明 {@link RequirePerm}
 * 或 {@link LoginOnly}。缺失则启动失败，杜绝「靠路径猜权限」回潮。
 */
@Component
public class PermContractChecker implements ApplicationRunner {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$");

    /** 纯认证/公开写接口（登录、OAuth 回调、健康检查等） */
    private static final Set<String> PUBLIC_AUTH_PREFIX = Set.of(
            "/api/auth",
            "/api/m/auth",
            "/api/m/wecom",
            "/api/m/dingtalk",
            "/api/trace/public",
            "/actuator",
            "/error",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources"
    );

    private final RequestMappingHandlerMapping handlerMapping;

    public PermContractChecker(
            @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
            RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();
        List<String> badCodes = new ArrayList<>();
        int checked = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> e : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = e.getKey();
            HandlerMethod hm = e.getValue();
            if (!isWrite(info)) {
                continue;
            }
            Set<String> paths = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : (info.getPatternsCondition() != null
                    ? info.getPatternsCondition().getPatterns()
                    : Set.of());
            if (paths.isEmpty()) {
                continue;
            }
            if (paths.stream().allMatch(PermContractChecker::isPublicAuth)) {
                continue;
            }
            RequirePerm perm = findPerm(hm);
            LoginOnly loginOnly = findLoginOnly(hm);
            if (loginOnly != null || perm != null) {
                checked++;
                if (perm != null) {
                    for (String code : perm.value()) {
                        if (code == null || code.isBlank() || !CODE.matcher(code.trim()).matches()) {
                            badCodes.add(hm + " -> invalid code: " + code);
                        }
                    }
                }
                continue;
            }
            missing.add(describe(info, hm));
        }

        if (!missing.isEmpty() || !badCodes.isEmpty()) {
            StringBuilder sb = new StringBuilder("写接口权限契约校验失败（PermContractChecker）\n");
            if (!missing.isEmpty()) {
                sb.append("缺少 @RequirePerm 或 @LoginOnly (").append(missing.size()).append("):\n");
                missing.forEach(m -> sb.append("  - ").append(m).append('\n'));
            }
            if (!badCodes.isEmpty()) {
                sb.append("权限码格式非法 (").append(badCodes.size()).append("):\n");
                badCodes.forEach(m -> sb.append("  - ").append(m).append('\n'));
            }
            throw new IllegalStateException(sb.toString());
        }
        // 启动成功：契约已强制
    }

    private static boolean isWrite(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        if (methods.isEmpty()) {
            // 未限制方法时，存在 Post/Put/Delete/Patch 映射注解的都视为写
            return true;
        }
        return methods.stream().anyMatch(m ->
                m != RequestMethod.GET && m != RequestMethod.HEAD && m != RequestMethod.OPTIONS);
    }

    private static boolean isPublicAuth(String path) {
        if (path == null) {
            return false;
        }
        String p = path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
        for (String pref : PUBLIC_AUTH_PREFIX) {
            if (p.equals(pref) || p.startsWith(pref + "/")) {
                return true;
            }
        }
        // /api/price/calc 为报价试算（脱敏），公开
        return p.startsWith("/api/price/calc");
    }

    private static RequirePerm findPerm(HandlerMethod hm) {
        RequirePerm a = hm.getMethodAnnotation(RequirePerm.class);
        if (a != null) {
            return a;
        }
        return hm.getBeanType().getAnnotation(RequirePerm.class);
    }

    private static LoginOnly findLoginOnly(HandlerMethod hm) {
        LoginOnly a = hm.getMethodAnnotation(LoginOnly.class);
        if (a != null) {
            return a;
        }
        return hm.getBeanType().getAnnotation(LoginOnly.class);
    }

    private static String describe(RequestMappingInfo info, HandlerMethod hm) {
        String methods = String.valueOf(info.getMethodsCondition().getMethods());
        String paths;
        if (info.getPathPatternsCondition() != null) {
            paths = String.valueOf(info.getPathPatternsCondition().getPatternValues());
        } else if (info.getPatternsCondition() != null) {
            paths = String.valueOf(info.getPatternsCondition().getPatterns());
        } else {
            paths = "?";
        }
        return methods + " " + paths + " -> " + hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();
    }
}
