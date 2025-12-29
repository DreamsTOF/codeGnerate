package com.dream.codegenerate.log;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.Builder;
import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作上下文 - 基于 Java 25 ScopedValue (正式版)
 * <p>
 * <b>核心设计：</b>
 * 1. 使用 ScopedValue 提供高性能线程上下文。
 * 2. <b>标识化设计：</b> 通过 isDefault 字段明确区分是“真实请求”还是“系统降级”。
 * </p>
 */
public class OperationContext {

    // 定义 ScopedValue 实例
    private static final ScopedValue<OperationInfo> CONTEXT = ScopedValue.newInstance();

    /**
     * 获取 ScopedValue 对象，用于切面绑定：
     * ScopedValue.where(OperationContext.getScopedValue(), info).run(() -> { ... });
     */
    public static ScopedValue<OperationInfo> getScopedValue() {
        return CONTEXT;
    }

    /**
     * 获取当前上下文信息
     * <p>
     * 逻辑：
     * 1. 如果已绑定：返回真实的业务上下文 (isDefault = false)。
     * 2. 如果未绑定：返回一个带标记的默认对象 (isDefault = true)。
     * </p>
     */
    public static OperationInfo get() {
        if (CONTEXT.isBound()) {
            return CONTEXT.get();
        }

        // 返回包含特殊标识的默认上下文
        return OperationInfo.builder()
                .isDefault(true) // <--- 特殊标识：记录本次是默认生成的
                .traceId("INTERNAL-" + System.currentTimeMillis())
                .startTime(System.currentTimeMillis())
                .businessModule("System")
                .businessAction("Background-Task")
                .clientIp("127.0.0.1")
                .userAgent("JDK-25-System")
                .build();
    }

    /**
     * 如果你更倾向于在某些场景下直接判空，可以使用此方法
     */
    public static OperationInfo getOrNull() {
        return CONTEXT.isBound() ? CONTEXT.get() : null;
    }

    // ========================================================================
    // 快捷访问器
    // ========================================================================

    public static String traceId() {
        return get().getTraceId();
    }

    /**
     * 检查当前上下文是否是真实的业务请求
     */
    public static boolean isReal() {
        return CONTEXT.isBound() && !CONTEXT.get().isDefault();
    }

//    // 使用 TTL 存储上下文，支持线程池传播
//    private static final ThreadLocal<OperationInfo> CONTEXT = new TransmittableThreadLocal<>();
//
//    /**
//     * 设置当前上下文信息
//     */
//    public static void set(OperationInfo info) {
//        CONTEXT.set(info);
//    }
//
//    /**
//     * 清理当前上下文信息 (必须在 finally 块调用)
//     */
//    public static void clear() {
//        CONTEXT.remove();
//    }
//
//    /**
//     * 获取当前上下文信息 (方法名保持不变，供全局调用)
//     */
//    public static OperationInfo get() {
//        OperationInfo info = CONTEXT.get();
//        if (info != null) {
//            return info;
//        }
//
//        return OperationInfo.builder()
//                .isDefault(true)
//                .traceId("INTERNAL-" + System.currentTimeMillis())
//                .startTime(System.currentTimeMillis())
//                .businessModule("System")
//                .businessAction("Background-Task")
//                .clientIp("127.0.0.1")
//                .userAgent("TTL-System")
//                .build();
//    }
//
//    /**
//     * 获取当前上下文信息，如果不存在则返回 null (方法名保持不变)
//     */
//    public static OperationInfo getOrNull() {
//        return CONTEXT.get();
//    }
//
//    /**
//     * 快捷获取当前 TraceId (方法名保持不变)
//     */
//    public static String traceId() {
//        return get().getTraceId();
//    }
//
//    /**
//     * 检查当前上下文是否是真实的业务请求
//     */
//    public static boolean isReal() {
//        OperationInfo info = CONTEXT.get();
//        return info != null && !info.isDefault();
//    }
    /**
     * 操作元数据模型
     */
    @Data
    @Builder
    public static class OperationInfo {

        /** * 特殊标识：是否为系统默认生成的上下文
         * true: 系统自动降级生成的 (如后台任务、未拦截的异步线程)
         * false: 真实的 Controller 拦截生成的
         */
        private boolean isDefault;

        // --- 追踪与时间 ---
        private String traceId;
        private long startTime;

        // --- 身份标识 ---
        private Long operatorId;
        private String operatorName;

        // --- 业务语义 ---
        private String businessModule;
        private String businessAction;

        // --- 终端信息 ---
        private String clientIp;
        private String userAgent;

        // --- 运行环境 ---
        private String methodName;
        private Object[] args;

        // --- 扩展字段 ---
        @Builder.Default
        private Map<String, Object> extensions = new HashMap<>();
    }
}
