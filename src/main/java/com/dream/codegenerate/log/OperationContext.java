package com.dream.codegenerate.log;

import lombok.Builder;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作上下文工具 (JDK 21+ ScopedValue 版)
 * <p>
 * 职责：使用 ScopedValue 在虚拟线程间安全地传递业务元数据。
 * 优化：增加了业务语义支持、终端信息抓取以及链式访问器。
 * </p>
 */
public class OperationContext {

    private static final ScopedValue<OperationInfo> CONTEXT = ScopedValue.newInstance();

    @Data
    @Builder
    public static class OperationInfo {
        // --- 核心追踪 ---
        private String traceId;       // 链路追踪 ID
        private long startTime;       // 请求开始时间

        // --- 业务身份 ---
        private Long operatorId;      // 操作人 ID
        private String operatorName;  // 操作人姓名 (审计日志直接显示，不用再去查库)

        // --- 业务语义 (由 AOP 从 OpenAPI/Swagger 抓取) ---
        private String businessModule; // 业务模块 (如：应用管理)
        private String businessAction; // 业务动作 (如：删除应用)

        // --- 终端信息 ---
        private String clientIp;      // 用户 IP
        private String userAgent;     // 浏览器/客户端标识

        // --- 运行时信息 ---
        private String methodName;    // 执行的 Java 方法名
        private Object[] args;        // 方法参数

        // --- 扩展包 ---
        @Builder.Default
        private Map<String, Object> extensions = new HashMap<>();

        public String getFullBusinessSummary() {
            if (businessModule != null && businessAction != null) {
                return businessModule + ":" + businessAction;
            }
            return businessAction != null ? businessAction : methodName;
        }
    }

    /**
     * 获取完整上下文对象
     */
    public static OperationInfo get() {
        return CONTEXT.orElse(null);
    }

    /**
     * 判断上下文是否已绑定
     */
    public static boolean isBound() {
        return CONTEXT.isBound();
    }

    // ========================================================================
    // 快捷访问器 (消除冗长的 null 检查和链式调用)
    // ========================================================================

    public static String traceId() {
        OperationInfo info = get();
        return info != null ? info.getTraceId() : "INTERNAL";
    }

    public static Long operatorId() {
        OperationInfo info = get();
        return info != null ? info.getOperatorId() : null;
    }

    public static String businessAction() {
        OperationInfo info = get();
        return info != null ? info.getFullBusinessSummary() : "Unknown";
    }

    /**
     * 暴露 ScopedValue 对象供切面或过滤器执行绑定：
     * OperationContext.getScopedValue().where(info).run(() -> { ... });
     */
    public static ScopedValue<OperationInfo> getScopedValue() {
        return CONTEXT;
    }
}
