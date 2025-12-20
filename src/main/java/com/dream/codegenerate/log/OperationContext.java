package com.dream.codegenerate.log;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 操作上下文工具 (Alibaba TTL 版)
 * <p>
 * 职责：使用 TransmittableThreadLocal 在线程间安全地传递业务元数据。
 * 注意：由于 ThreadLocal 不会自动销毁，必须在请求结束时手动调用 clear()。
 * 建议：通过 Spring Interceptor 或 try-with-resources 模式调用。
 * </p>
 */
@Builder
public class OperationContext {

    private static final ThreadLocal<OperationInfo> CONTEXT = new TransmittableThreadLocal<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperationInfo {
        // --- 核心追踪 ---
        private String traceId;       // 链路追踪 ID
        private long startTime;       // 请求开始时间

        // --- 业务身份 ---
        private Long operatorId;      // 操作人 ID
        private String operatorName;  // 操作人姓名

        // --- 业务语义 (由 AOP 从 OpenAPI/Swagger 抓取) ---
        private String businessModule; // 业务模块
        private String businessAction; // 业务动作

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
     * 手动设置上下文
     */
    public static void set(OperationInfo info) {
        CONTEXT.set(info);
    }

    /**
     * 【推荐】绑定上下文并返回一个可自动关闭的句柄
     * 使用示例：
     * try (var handle = OperationContext.bind(info)) {
     * // 业务逻辑...
     * } // 退出块时自动调用 clear()
     */
    public static ContextHandle bind(OperationInfo info) {
        set(info);
        return new ContextHandle();
    }

    /**
     * 获取当前上下文
     */
    public static OperationInfo get() {
        return CONTEXT.get();
    }

    /**
     * 清理上下文 (极其重要：防止 ThreadLocal 内存泄漏)
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 判断上下文是否存在
     */
    public static boolean isPresent() {
        return CONTEXT.get() != null;
    }

    // ========================================================================
    // 自动清理句柄类
    // ========================================================================

    public static class ContextHandle implements AutoCloseable {
        @Override
        public void close() {
            clear();
        }
    }

    // ========================================================================
    // 快捷访问器
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
}
