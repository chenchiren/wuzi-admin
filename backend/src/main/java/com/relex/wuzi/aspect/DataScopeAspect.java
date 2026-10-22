package com.relex.wuzi.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.relex.wuzi.annotation.DataScope;
import com.relex.wuzi.domain.SysUser;
import com.relex.wuzi.domain.Warehouse;
import com.relex.wuzi.mapper.SysUserMapper;
import com.relex.wuzi.mapper.WarehouseMapper;
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
 * 数据权限切面：拦截 @DataScope 方法，解析当前用户可见的部门/用户/仓库范围，
 * 写入 DataScopeContext，查询侧用 DataScopeSupport.apply 拼条件。
 * 使用 Spring AOP MethodInterceptor（不依赖 AspectJ）。
 */
@Component
@RequiredArgsConstructor
public class DataScopeAspect extends StaticMethodMatcherPointcutAdvisor implements MethodInterceptor {

    private static final List<String> ADMIN_ROLES = List.of("ADMIN");

    private final SysUserMapper sysUserMapper;
    private final WarehouseMapper warehouseMapper;

    /** 作为 Advisor 的 advice 指向自身 */
    @jakarta.annotation.PostConstruct
    public void initAdvice() {
        setAdvice(this);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return AnnotatedElementUtils.hasAnnotation(method, DataScope.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, DataScope.class);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        DataScope scope = findScope(invocation.getMethod(), invocation.getThis());
        if (scope == null) {
            return invocation.proceed();
        }
        try {
            DataScopeContext.set(buildRule(scope));
            return invocation.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    private DataScope findScope(Method method, Object target) {
        DataScope onMethod = AnnotatedElementUtils.findMergedAnnotation(method, DataScope.class);
        if (onMethod != null) {
            return onMethod;
        }
        Class<?> targetClass = target != null ? target.getClass() : method.getDeclaringClass();
        return AnnotatedElementUtils.findMergedAnnotation(targetClass, DataScope.class);
    }

    private DataScopeRule buildRule(DataScope scope) {
        DataScopeRule rule = new DataScopeRule();
        rule.setDeptField(scope.deptField());
        rule.setUserField(scope.userField());
        rule.setWarehouseField(scope.warehouseField());

        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
            rule.setAll(true);
            return rule;
        }
        HttpServletRequest request = servletAttrs.getRequest();
        Long userId = attrLong(request.getAttribute("userId"));
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("roles");
        rule.setUserId(userId);
        rule.setRoles(roles == null ? List.of() : roles);

        if (userId == null || isAdmin(roles)) {
            rule.setAll(true);
            return rule;
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            rule.setAll(true);
            return rule;
        }
        rule.setDeptId(user.getDeptId());
        rule.setCompanyId(user.getCompanyId());
        rule.setDeptIds(user.getDeptId() == null ? List.of() : List.of(user.getDeptId()));

        List<Warehouse> warehouses = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getManagerId, userId)
                .eq(Warehouse::getIsDeleted, 0));
        rule.setWarehouseIds(warehouses.stream().map(Warehouse::getId).toList());
        return rule;
    }

    private boolean isAdmin(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        return roles.stream().anyMatch(ADMIN_ROLES::contains);
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
