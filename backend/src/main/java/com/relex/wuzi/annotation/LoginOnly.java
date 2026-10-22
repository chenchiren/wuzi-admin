package com.relex.wuzi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅要求登录、不要求按钮权限的操作（自助服务）。
 * 启动契约校验 {@code PermContractChecker} 允许此类写接口缺少 {@link RequirePerm}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginOnly {

    /** 说明用途，便于审计 */
    String value() default "";
}
