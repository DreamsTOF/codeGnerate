package com.dream.codegenerate.log.core;

import org.slf4j.Logger;

/**
 * 业务专用日志接口 (MyLogger)
 * 继承 SLF4J Logger 以确保向下兼容，同时提供基于枚举的高性能扩展点。
 */
public interface MyLogger extends Logger {

    /**
     * 核心扩展点：支持传入任意 LogLevel 枚举进行调用
     * 只要在 LogLevel 中增加枚举成员，此方法即可自动支持新级别。
     */
    boolean isLogLevelEnabled(LogLevel level);
    void log(LogLevel level, String msg);
    void log(LogLevel level, String format, Object arg);
    void log(LogLevel level, String format, Object... args);
    void log(LogLevel level, String msg, Throwable t);

    // ========================================================================
    // 快捷方式：NOTICE 级别
    // ========================================================================
    boolean isNoticeEnabled();
    void notice(String msg);
    void notice(String format, Object arg);
    void notice(String format, Object... arguments);
    void notice(String msg, Throwable t);

    // ========================================================================
    // 快捷方式：CRITICAL 级别
    // ========================================================================
    boolean isCriticalEnabled();
    void critical(String msg);
    void critical(String format, Object arg);
    void critical(String format, Object... arguments);
    void critical(String msg, Throwable t);

    // ========================================================================
    // 快捷方式：FATAL 级别
    // ========================================================================
    boolean isFatalEnabled();
    void fatal(String msg);
    void fatal(String format, Object arg);
    void fatal(String format, Object... arguments);
    void fatal(String msg, Throwable t);
}
