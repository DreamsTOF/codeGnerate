package com.dream.codegenerate.utils.smartQuery;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【DTO 查询映射注解】
 * 专用于 Query DTO 字段，声明该字段对应哪个 Entity 的哪个属性进行 SQL 构建。
 * <p>
 * 优先级：手动 map() > @QueryMapping > 自动推断
 * </p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueryMapping {
    /**
     * 目标实体类 (例如 Dept.class)
     */
    Class<?> targetEntity();

    /**
     * 目标属性名 (例如 "code")
     */
    String targetProperty();

    /**
     * 本地关联键 (可选)
     * <p>如果不填，默认使用当前被注解的字段名作为本地关联键。</p>
     * <p>仅当需要【跨库子查询】时才需要填写此字段，指明主表中用于关联的外键 (如 "deptId")。</p>
     * <p>如果只是在 Join 树中查找，留空即可。</p>
     */
    String localKey() default "";

    /**
     * 匹配方式 (可选)
     * <p>默认为 CUSTOM，即根据字段类型自动推断 (String->LIKE, Number->EQ)。</p>
     */
    MatchType matchType() default MatchType.CUSTOM;
}
