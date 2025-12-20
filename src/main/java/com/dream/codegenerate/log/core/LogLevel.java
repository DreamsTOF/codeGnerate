package com.dream.codegenerate.log.core;
/**
     * 内部日志级别枚举 (已扩展为8个级别)
     * 后续只需在此处增加枚举，并指定正确的 weight 即可
     */
public enum LogLevel {
        // 1. 细粒度追踪
        TRACE(0),
        // 2. 调试
        DEBUG(10),
        // 3. 正常信息
        INFO(20),
        // 4. 注意 (新增) - 比 INFO 重要，但不是错误
        NOTICE(30),
        // 5. 警告
        WARN(40),
        // 6. 错误
        ERROR(50),
        // 7. 严重错误 (新增) - 系统组件不可用
        CRITICAL(60),
        // 8. 致命错误 (新增) - 系统崩溃
        FATAL(100);

        public final int weight;
        LogLevel(int weight) { this.weight = weight; }
    }
