package com.dream.codegenerate.log.autoAudit;

import java.lang.annotation.*;

/**
 * 关联引用注解 (Reference Translation)
 * <p>
 * 标记在 ID 字段上，用于指示审计引擎将 ID 翻译为业务名称。
 * 支持容错降级：如果目标表不存在，自动回退显示原始 ID。
 * </p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditReference {

    /**
     * 目标实体类
     * 默认为 void.class (容错占位符)
     */
    Class<?> target() default void.class;

    /**
     * 目标展示字段名 (如: name, username)
     */
    String label() default "";

    /**
     * 目标表主键列名
     */
    String idCol() default "id";
}
