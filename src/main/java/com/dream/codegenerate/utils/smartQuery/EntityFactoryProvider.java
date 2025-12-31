package com.dream.codegenerate.utils.smartQuery;

/**
 * 实体工厂提供者接口
 * <p>
 * 实现此接口的 Bean 会被自动注册到 SmartQueryConfig 中。
 * </p>
 * @param <E> 实体类型
 */
public interface EntityFactoryProvider<E> {
    /**
     * 获取实体类型
     */
    Class<E> getEntityClass();

    /**
     * 获取工厂实现
     */
    SmartQueryAssembler.EntityFactory<E> getFactory();
}
