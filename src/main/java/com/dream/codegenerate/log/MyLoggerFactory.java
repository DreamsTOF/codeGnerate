package com.dream.codegenerate.log;

import com.dreamtof.log.core.MyLogger;
import org.slf4j.LoggerFactory;

/**
 * MyLogger 工厂类
 * 专门适配 Lombok @CustomLog 的解析需求
 */
public class MyLoggerFactory {

    public static MyLogger getLogger(Class<?> clazz) {
        return new MyLoggerWrapper(LoggerFactory.getLogger(clazz));
    }

    public static MyLogger getLogger(String topic, Class<?> clazz) {
        if (topic == null || topic.isEmpty()) {
            return getLogger(clazz);
        }
        return new MyLoggerWrapper(LoggerFactory.getLogger(topic));
    }

    public static MyLogger getLogger(String topic) {
        if (topic == null || topic.isEmpty()) {
            return new MyLoggerWrapper(LoggerFactory.getLogger(MyLoggerFactory.class));
        }
        return new MyLoggerWrapper(LoggerFactory.getLogger(topic));
    }
}
