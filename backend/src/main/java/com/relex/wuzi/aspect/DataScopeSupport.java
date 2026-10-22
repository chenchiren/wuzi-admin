package com.relex.wuzi.aspect;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 将 DataScopeRule 拼进 MyBatis-Plus Wrapper（按注解声明的列名）。
 */
public final class DataScopeSupport {

    private DataScopeSupport() {
    }

    /** 对任意 Wrapper 追加数据权限条件 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void apply(Wrapper<?> wrapper) {
        DataScopeRule rule = DataScopeContext.get();
        if (rule == null || rule.isAll()) {
            return;
        }
        AbstractWrapper aw = asAbstract(wrapper);
        if (aw == null) {
            return;
        }

        String deptField = blankToNull(rule.getDeptField());
        String userField = blankToNull(rule.getUserField());
        String warehouseField = blankToNull(rule.getWarehouseField());

        boolean hasUserScope = (deptField != null && notEmpty(rule.getDeptIds()))
                || (userField != null && rule.getUserId() != null);
        if (hasUserScope) {
            StringBuilder cond = new StringBuilder();
            if (deptField != null && notEmpty(rule.getDeptIds())) {
                String ids = rule.getDeptIds().stream().map(String::valueOf).collect(Collectors.joining(","));
                cond.append(deptField).append(" IN (").append(ids).append(")");
            }
            if (userField != null && rule.getUserId() != null) {
                if (cond.length() > 0) {
                    cond.append(" OR ");
                }
                cond.append(userField).append(" = ").append(rule.getUserId());
            }
            aw.apply("(" + cond + ")");
        }

        if (warehouseField != null) {
            List<Long> whIds = rule.getWarehouseIds();
            if (whIds == null) {
                aw.isNull(warehouseField);
            } else if (whIds.isEmpty()) {
                aw.apply("1 = 0");
            } else {
                aw.in(warehouseField, whIds);
            }
        }
    }

    /** 常用入口：Lambda 查询包装器 */
    public static <T> void apply(LambdaQueryWrapper<T> wrapper) {
        apply((Wrapper<?>) wrapper);
    }

    public static <T> void apply(LambdaUpdateWrapper<T> wrapper) {
        apply((Wrapper<?>) wrapper);
    }

    public static void apply(QueryWrapper<?> wrapper) {
        apply((Wrapper<?>) wrapper);
    }

    public static void apply(UpdateWrapper<?> wrapper) {
        apply((Wrapper<?>) wrapper);
    }

    @SuppressWarnings("rawtypes")
    private static AbstractWrapper asAbstract(Wrapper<?> wrapper) {
        if (wrapper instanceof AbstractWrapper aw) {
            return aw;
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }
}
