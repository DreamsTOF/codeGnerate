package com.dream.codegenerate.utils.autoAudit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * 用于标记 Service 层方法，触发数据变更对比
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    /** 业务模块 */
    String module() default "";
    /** 业务动作描述 (如: 修改用户信息) */
    String action() default "";
    /** 实体类类型 (用于自动查询旧值) */
    Class<?> entity() default Object.class;
}
