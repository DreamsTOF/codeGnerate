package com.dream.codegenerate.log.core;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * SLF4J 2.0+ 服务提供者实现 (Spring Boot 3/4 标准绑定方式)
 * <p>
 * 替代了旧版的 StaticLoggerBinder。
 * </p>
 */
public class CustomSLF4JServiceProvider implements SLF4JServiceProvider {

    // 声明必须的组件
    private ILoggerFactory loggerFactory;
    private IMarkerFactory markerFactory;
    private MDCAdapter mdcAdapter;

    /**
     * 获取日志工厂 (返回我们自定义的工厂)
     */
    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    /**
     * 获取 Marker 工厂 (使用 SLF4J 提供的默认基础实现即可)
     */
    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    /**
     * 获取 MDC 适配器 (使用 SLF4J 提供的默认基础实现即可)
     */
    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    /**
     * 声明支持的 API 版本
     */
    @Override
    public String getRequestedApiVersion() {
        return "2.0.99"; // 声明兼容 SLF4J 2.0.x
    }

    /**
     * 初始化方法 (SLF4J 会在启动时调用此方法)
     */
    @Override
    public void initialize() {
        this.loggerFactory = new CustomLoggerFactory();
        this.markerFactory = new BasicMarkerFactory();
        this.mdcAdapter = new BasicMDCAdapter();
    }
}
