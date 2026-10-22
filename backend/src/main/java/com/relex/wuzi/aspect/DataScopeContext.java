package com.relex.wuzi.aspect;

/**
 * 数据权限 ThreadLocal 持有器。
 */
public final class DataScopeContext {

    private static final ThreadLocal<DataScopeRule> HOLDER = new ThreadLocal<>();

    private DataScopeContext() {
    }

    public static void set(DataScopeRule rule) {
        HOLDER.set(rule);
    }

    public static DataScopeRule get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
