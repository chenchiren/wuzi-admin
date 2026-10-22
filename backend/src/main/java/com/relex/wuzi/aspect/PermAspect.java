package com.relex.wuzi.aspect;

import com.relex.wuzi.annotation.RequirePerm;
import com.relex.wuzi.common.BizException;
import com.relex.wuzi.security.JwtAuthFilter;
import com.relex.wuzi.service.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 按钮级权限切面：拦截 @RequirePerm 方法。
 */
@Component
@RequiredArgsConstructor
public class PermAspect extends StaticMethodMatcherPointcutAdvisor implements MethodInterceptor {

    private final PermissionService permissionService;

    @jakarta.annotation.PostConstruct
    public void initAdvice() {
        setAdvice(this);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return AnnotatedElementUtils.hasAnnotation(method, RequirePerm.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, RequirePerm.class);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        RequirePerm ann = find(invocation.getMethod(), invocation.getThis());
        if (ann == null) {
            return invocation.proceed();
        }
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            return invocation.proceed();
        }
        HttpServletRequest request = servletAttrs.getRequest();
        Long userId = attrLong(request.getAttribute("userId"));
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("roles");

        if (!permissionService.hasPerm(userId, roles, null, ann.requireAll(), ann.value())) {
            throw new BizException(403, "无权限执行该操作");
        }
        return invocation.proceed();
    }

    private RequirePerm find(Method method, Object target) {
        RequirePerm onMethod = AnnotatedElementUtils.findMergedAnnotation(method, RequirePerm.class);
        if (onMethod != null) {
            return onMethod;
        }
        Class<?> targetClass = target != null ? target.getClass() : method.getDeclaringClass();
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, RequirePerm.class);
    }

    private Long attrLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Long l) {
            return l;
        }
        return Long.valueOf(v.toString());
    }
}
