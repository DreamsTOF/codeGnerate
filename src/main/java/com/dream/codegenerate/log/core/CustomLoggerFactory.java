package com.dream.codegenerate.log.core;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SLF4J Logger 工厂实现
 * <p>
 * 职责：负责创建和缓存 CustomLogImpl 实例，确保同名的 Logger 实例是单例。
 * </p>
 */
public class CustomLoggerFactory implements ILoggerFactory {

    // 缓存 Logger 实例，确保线程安全和高性能
    private final ConcurrentMap<String, Logger> loggerMap = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(String name) {
        // 使用 computeIfAbsent 实现线程安全的缓存和创建
        return loggerMap.computeIfAbsent(name, CustomLogImpl::new);
    }
}
