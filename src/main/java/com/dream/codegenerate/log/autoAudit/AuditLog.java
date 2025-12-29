package com.dream.codegenerate.log.autoAudit;

import java.lang.annotation.*;

/**
 * 审计日志注解 (Audit Log Annotation) - 最终版
 * <p>
 * <b>核心能力：</b>
 * 1. 声明式审计：标记在 Service 方法上，自动记录数据变更。
 * 2. 乐观锁集成：retry 参数控制并发重试。
 * 3. 策略控制：partial/skipNull/systemFields 精细控制对比行为。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 业务模块 (留空自动继承 Controller Tag 或 微服务名)
     */
    String module() default "";

    /**
     * 业务动作 (留空自动继承 Controller Operation)
     */
    String action() default "";

    /**
     * 单实体模式 (兼容旧代码)
     */
    Class<?> entity() default Object.class;

    /**
     * 多实体模式 (推荐，支持一次操作修改多张表)
     */
    Class<?>[] entities() default {};

    /**
     * 乐观锁冲突重试次数
     * 0: 关闭自动重试 (默认)
     * >0: 开启重试 (适用于幂等写操作)
     */
    int retry() default 0;

    /**
     * 重试线性退避基数 (毫秒)
     */
    long backoff() default 50L;

    /**
     * 简略模式 (Partial Mode)
     * <p>
     * true: 新增/删除操作只记录摘要，不记录全量字段详情。
     * false (默认): 新增/删除也会记录所有字段值。
     * </p>
     */
    boolean partial() default false;

    /**
     * 跳过空字段 (Skip Null Fields)
     * <p>
     * true (默认): DTO 中为 null 的字段跳过对比 (适用于部分更新)。
     * false: 即使新值为 null 也进行对比 (记录"字段被置空")。
     * </p>
     */
    boolean skipNull() default true;

    /**
     * 包含系统字段 (Include System Fields)
     * <p>
     * true: 记录 updateTime/createTime 等自动填充字段的变更。
     * false (默认): 忽略系统字段，只关注业务数据。
     * </p>
     */
    boolean systemFields() default false;
}
