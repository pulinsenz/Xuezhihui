package com.agent.rag.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 角色权限注解：标注在 Controller 类或方法上，要求当前用户具备指定角色
 * <p>
 * 由 {@link com.agent.rag.interceptor.LoginInterceptor} 通过 HandlerMethod 反射校验
 *
 * @author pulinsenz
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    /**
     * 要求的角色，默认管理员
     */
    String value() default "admin";
}
