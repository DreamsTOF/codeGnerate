package com.dream.codegenerate.utils.smartQuery;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【对象级联】
 * 声明一个嵌套的 VO 或 VO 集合 (支持 1:1, 1:N)。
 * 引擎会递归解析该 VO 的字段并进行 Join 查询。
 */
/**
 * 【对象级联】
 * 声明一个嵌套的 VO 或 VO 集合 (支持 1:1, 1:N)。
 * 引擎会递归解析该 VO 的字段并进行 Join 查询。
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SmartFetch {
    Class<?> targetEntity();

    /** 本地关联键 (Entity属性名) */
    String localField();

    /** 远程关联键 (留空默认为目标表主键) */
    String remoteFieldLink() default "";

    /**
     * 抓取策略
     * 默认为 AUTO：智能识别嵌套深度，自动拆分查询以防止笛卡尔积爆炸。
     */
    FetchType fetchType() default FetchType.AUTO;
}
