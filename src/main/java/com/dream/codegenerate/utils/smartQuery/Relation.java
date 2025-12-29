package com.dream.codegenerate.utils.smartQuery;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【字段映射】声明抓取单个列
 * 例如：UserVO -> @Relation String deptName;
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Relation {
    Class<?> targetEntity();

    /** 本地关联键 */
    String localField();

    /** 目标列名 (必填) */
    String remoteField();

    /** 远程关联键 (留空默认对方主键) */
    String remoteFieldLink() default "";

    /** 反向过滤模式 */
    MatchType matchType() default MatchType.CUSTOM;
}
