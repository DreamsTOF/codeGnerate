package com.dream.codegenerate.utils.smartQuery;

import com.dream.codegenerate.utils.smartQuery.EntityFactoryProvider;
import com.dream.codegenerate.utils.smartQuery.SmartQueryAssembler.EntityFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SmartQuery 全局配置中心
 * <p>
 * 1. 维护全局实体工厂注册表 (FACTORIES)。
 * 2. 利用 Spring 自动扫描所有 EntityFactoryProvider 并注册。
 * 3. 供 FlexSmartQuery 静态访问，实现"零配置"自动加速。
 * </p>
 */
@Configuration
public class SmartQueryConfig {

    /**
     * 全局工厂注册表 (线程安全，静态访问)
     * FlexSmartQuery 会直接读取这里，无需用户手动传递
     */
    public static final Map<Class<?>, EntityFactory<?>> FACTORIES = new ConcurrentHashMap<>();

    /**
     * Spring 启动时自动收集所有工厂提供者
     */
    @Bean
    public Map<Class<?>, EntityFactory<?>> smartQueryFactories(List<EntityFactoryProvider<?>> providers) {
        if (providers != null) {
            for (EntityFactoryProvider<?> provider : providers) {
                FACTORIES.put(provider.getEntityClass(), provider.getFactory());
            }
        }
        return FACTORIES;
    }
}
