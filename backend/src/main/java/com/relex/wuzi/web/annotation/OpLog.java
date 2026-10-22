package com.relex.wuzi.web.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 Controller 方法上，由 OpLogAspect 异步落库。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 模块名，如「审批流」「消息中心」 */
    String module();

    /** 动作名，如「发起审批」「审批通过」 */
    String action();

    /** 备注说明 */
    String description() default "";
}
