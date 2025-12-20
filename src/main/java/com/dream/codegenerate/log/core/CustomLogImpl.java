package com.dream.codegenerate.log.core;


import com.dream.codegenerate.log.OperationContext;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

/**
 * 结构化异步日志实现
 * 集成外部配置读取
 */
public class CustomLogImpl implements Logger {

    private final String name;
    private volatile LogLevel minLogLevel;

    public CustomLogImpl(String name) {
        this.name = name;
        this.minLogLevel = loadConfiguredLevel();

        // 初始化时发一条调试日志
        if (this.name.equals(CustomLogImpl.class.getName())) {
            LogWriter.enqueue(new LogEvent(LogLevel.INFO, "Log System Initialized. Level: " + minLogLevel, null, null, name));
        }
    }

    /**
     * [建议2] 外部化配置加载
     * 优先读取 System Property (-Dlog.level=DEBUG)
     * 其次读取环境变量 (LOG_LEVEL=DEBUG)
     * 默认 INFO
     */
    private LogLevel loadConfiguredLevel() {
        String levelStr = System.getProperty("log.level");
        if (levelStr == null || levelStr.isEmpty()) {
            levelStr = System.getenv("LOG_LEVEL");
        }

        if (levelStr != null) {
            try {
                return LogLevel.valueOf(levelStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 配置错误时回退到 INFO
                System.err.println("⚠️ Invalid log level config: " + levelStr + ", using INFO.");
            }
        }
        return LogLevel.INFO;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isLevelEnabled(LogLevel level) {
        return level.weight >= minLogLevel.weight;
    }

    private void logInternal(LogLevel level, String message, Throwable t) {
        // 双重检查：这里是最后的防线
        if (!isLevelEnabled(level)) {
            return;
        }

        OperationContext.OperationInfo context = OperationContext.get();
        LogEvent event = new LogEvent(level, message, t, context, name);
        LogWriter.enqueue(event);
    }

    // ========================================================================
    // 标准 Logger 接口实现 (委托给 logInternal)
    // ========================================================================

    @Override public boolean isTraceEnabled() { return isLevelEnabled(LogLevel.TRACE); }
    @Override public void trace(String msg) { logInternal(LogLevel.TRACE, msg, null); }
    @Override public void trace(String format, Object arg) { if(isTraceEnabled()) logInternal(LogLevel.TRACE, format(format, arg), null); }
    @Override public void trace(String format, Object arg1, Object arg2) { if(isTraceEnabled()) logInternal(LogLevel.TRACE, format(format, arg1, arg2), null); }
    @Override public void trace(String format, Object... arguments) { if(isTraceEnabled()) logInternal(LogLevel.TRACE, format(format, arguments), null); }
    @Override public void trace(String msg, Throwable t) { logInternal(LogLevel.TRACE, msg, t); }

    @Override public boolean isDebugEnabled() { return isLevelEnabled(LogLevel.DEBUG); }
    @Override public void debug(String msg) { logInternal(LogLevel.DEBUG, msg, null); }
    @Override public void debug(String format, Object arg) { if(isDebugEnabled()) logInternal(LogLevel.DEBUG, format(format, arg), null); }
    @Override public void debug(String format, Object arg1, Object arg2) { if(isDebugEnabled()) logInternal(LogLevel.DEBUG, format(format, arg1, arg2), null); }
    @Override public void debug(String format, Object... arguments) { if(isDebugEnabled()) logInternal(LogLevel.DEBUG, format(format, arguments), null); }
    @Override public void debug(String msg, Throwable t) { logInternal(LogLevel.DEBUG, msg, t); }

    @Override public boolean isInfoEnabled() { return isLevelEnabled(LogLevel.INFO); }
    @Override public void info(String msg) { logInternal(LogLevel.INFO, msg, null); }
    @Override public void info(String format, Object arg) { if(isInfoEnabled()) logInternal(LogLevel.INFO, format(format, arg), null); }
    @Override public void info(String format, Object arg1, Object arg2) { if(isInfoEnabled()) logInternal(LogLevel.INFO, format(format, arg1, arg2), null); }
    @Override public void info(String format, Object... arguments) { if(isInfoEnabled()) logInternal(LogLevel.INFO, format(format, arguments), null); }
    @Override public void info(String msg, Throwable t) { logInternal(LogLevel.INFO, msg, t); }

    @Override public boolean isWarnEnabled() { return isLevelEnabled(LogLevel.WARN); }
    @Override public void warn(String msg) { logInternal(LogLevel.WARN, msg, null); }
    @Override public void warn(String format, Object arg) { logInternal(LogLevel.WARN, format(format, arg), null); } // WARN 通常不节省格式化开销
    @Override public void warn(String format, Object... arguments) { logInternal(LogLevel.WARN, format(format, arguments), null); }
    @Override public void warn(String format, Object arg1, Object arg2) { logInternal(LogLevel.WARN, format(format, arg1, arg2), null); }
    @Override public void warn(String msg, Throwable t) { logInternal(LogLevel.WARN, msg, t); }

    @Override public boolean isErrorEnabled() { return isLevelEnabled(LogLevel.ERROR); }
    @Override public void error(String msg) { logInternal(LogLevel.ERROR, msg, null); }
    @Override public void error(String format, Object arg) { logInternal(LogLevel.ERROR, format(format, arg), null); }
    @Override public void error(String format, Object arg1, Object arg2) { logInternal(LogLevel.ERROR, format(format, arg1, arg2), null); }
    @Override public void error(String format, Object... arguments) { logInternal(LogLevel.ERROR, format(format, arguments), null); }
    @Override public void error(String msg, Throwable t) { logInternal(LogLevel.ERROR, msg, t); }

    // Marker 接口省略实现，直接委托给普通方法
    @Override public boolean isTraceEnabled(Marker marker) { return isTraceEnabled(); }
    @Override public void trace(Marker marker, String msg) { trace(msg); }
    @Override public void trace(Marker marker, String format, Object arg) { trace(format, arg); }
    @Override public void trace(Marker marker, String format, Object arg1, Object arg2) { trace(format, arg1, arg2); }
    @Override public void trace(Marker marker, String format, Object... argArray) { trace(format, argArray); }
    @Override public void trace(Marker marker, String msg, Throwable t) { trace(msg, t); }

    @Override public boolean isDebugEnabled(Marker marker) { return isDebugEnabled(); }
    @Override public void debug(Marker marker, String msg) { debug(msg); }
    @Override public void debug(Marker marker, String format, Object arg) { debug(format, arg); }
    @Override public void debug(Marker marker, String format, Object arg1, Object arg2) { debug(format, arg1, arg2); }
    @Override public void debug(Marker marker, String format, Object... arguments) { debug(format, arguments); }
    @Override public void debug(Marker marker, String msg, Throwable t) { debug(msg, t); }

    @Override public boolean isInfoEnabled(Marker marker) { return isInfoEnabled(); }
    @Override public void info(Marker marker, String msg) { info(msg); }
    @Override public void info(Marker marker, String format, Object arg) { info(format, arg); }
    @Override public void info(Marker marker, String format, Object arg1, Object arg2) { info(format, arg1, arg2); }
    @Override public void info(Marker marker, String format, Object... arguments) { info(format, arguments); }
    @Override public void info(Marker marker, String msg, Throwable t) { info(msg, t); }

    @Override public boolean isWarnEnabled(Marker marker) { return isWarnEnabled(); }
    @Override public void warn(Marker marker, String msg) { warn(msg); }
    @Override public void warn(Marker marker, String format, Object arg) { warn(format, arg); }
    @Override public void warn(Marker marker, String format, Object arg1, Object arg2) { warn(format, arg1, arg2); }
    @Override public void warn(Marker marker, String format, Object... arguments) { warn(format, arguments); }
    @Override public void warn(Marker marker, String msg, Throwable t) { warn(msg, t); }

    @Override public boolean isErrorEnabled(Marker marker) { return isErrorEnabled(); }
    @Override public void error(Marker marker, String msg) { error(msg); }
    @Override public void error(Marker marker, String format, Object arg) { error(format, arg); }
    @Override public void error(Marker marker, String format, Object arg1, Object arg2) { error(format, arg1, arg2); }
    @Override public void error(Marker marker, String format, Object... arguments) { error(format, arguments); }
    @Override public void error(Marker marker, String msg, Throwable t) { error(msg, t); }

    private String format(String format, Object... args) {
        return MessageFormatter.arrayFormat(format, args).getMessage();
    }
    private String format(String format, Object arg) {
        return MessageFormatter.format(format, arg).getMessage();
    }
    private String format(String format, Object arg1, Object arg2) {
        return MessageFormatter.format(format, arg1, arg2).getMessage();
    }
}
