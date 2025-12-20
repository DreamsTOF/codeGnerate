package com.dream.codegenerate.log.core;


import com.dream.codegenerate.log.OperationContext;

/**
 * 异步日志事件对象 (POJO)
 * 职责：封装需要异步写入的日志信息和操作上下文。
 */
public class LogEvent {
    // 日志级别
    private final LogLevel level;
    // 日志消息
    private final String message;
    // 原始异常，可能为 null
    private final Throwable throwable;
    // 操作上下文 (ScopedValue中的TraceId、方法名等)
    private final OperationContext.OperationInfo context;
    // Logger 名称
    private final String loggerName;
    // 记录时间戳 (在创建事件时立即获取，以保持准确性)
    private final long timestamp;

    public LogEvent(LogLevel level, String message, Throwable throwable,
                    OperationContext.OperationInfo context, String loggerName) {
        this.level = level;
        this.message = message;
        this.throwable = throwable;
        this.context = context;
        this.loggerName = loggerName;
        this.timestamp = System.currentTimeMillis(); // 在业务线程中捕获时间
    }

    // ========================================================================
    // Getter 方法 (用于LogWriter线程读取)
    // ========================================================================

    public LogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public OperationContext.OperationInfo getContext() {
        return context;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
