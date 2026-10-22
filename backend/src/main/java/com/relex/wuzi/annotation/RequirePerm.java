package com.relex.wuzi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级权限：校验当前用户是否拥有指定权限码。
 * 码格式：资源:动作，如 material:create / po:apply:submit。
 * ADMIN 角色默认放行。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePerm {

    /** 所需权限码，任一满足即可 */
    String[] value();

    /** 逻辑：true=全部满足（AND），默认任一满足（OR） */
    boolean requireAll() default false;
}
