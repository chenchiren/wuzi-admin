package com.relex.wuzi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限：按部门 / 用户 / 仓库过滤可见数据。
 * 列名使用数据库下划线命名，查询侧通过 DataScopeSupport 拼接到 Wrapper。
 * 已接入：发票/结算、采购申请、领用申请等按部门/申请人过滤的列表。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 机构部门列，如 dept_id */
    String deptField() default "";

    /** 归属用户列，如 created_by / purchase_user_id */
    String userField() default "";

    /** 仓库列，如 warehouse_id */
    String warehouseField() default "";
}
