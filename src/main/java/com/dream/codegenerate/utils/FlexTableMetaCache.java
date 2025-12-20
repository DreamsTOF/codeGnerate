package com.dream.codegenerate.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * ==================================================================================
 * 🚀 FlexTableMetaCache - MyBatis-Flex 表元数据缓存中心
 * ==================================================================================
 * 【功能描述】：
 * 解决 MyBatis-Flex 中 QueryColumn 与 Entity 属性名频繁转换带来的反射开销。
 * * 【主要用途】：
 * 1. 提供 Column(列名) -> Property(属性名) 的高效映射查询。
 * 2. 集中管理元数据缓存，支持多组件共享（如导出工具、智能查询器等）。
 * ==================================================================================
 */
public class FlexTableMetaCache {

    /**
     * 核心映射缓存：Class -> { Column : Property }
     * 缓存策略：最大 1000 个实体类，1小时内未访问则过期
     */
    private static final Cache<Class<?>, Map<String, String>> PROPERTY_MAPPING_CACHE = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    /**
     * 获取指定实体的字段映射表
     * * @param entityClass 实体类类型
     * @return 映射关系 Map (Key: 数据库列名, Value: Java 属性名)
     */
    public static Map<String, String> getPropertyMapping(Class<?> entityClass) {
        return PROPERTY_MAPPING_CACHE.get(entityClass, clazz -> {
            TableInfo tableInfo = TableInfoFactory.ofEntityClass(clazz);
            Map<String, String> map = new HashMap<>();

            // 注入普通字段
            Optional.ofNullable(tableInfo.getColumnInfoList())
                    .ifPresent(list -> list.forEach(i -> map.put(i.getColumn(), i.getProperty())));

            // 注入主键字段
            Optional.ofNullable(tableInfo.getPrimaryKeyList())
                    .ifPresent(list -> list.forEach(pk -> map.put(pk.getColumn(), pk.getProperty())));

            return Map.copyOf(map);
        });
    }

    /**
     * 根据 QueryColumn 获取其在实体类中的属性名
     * * @param entityClass 实体类类型
     * @param columnName  列名
     * @return 属性名（如果找不到则返回原列名）
     */
    public static String getPropertyName(Class<?> entityClass, String columnName) {
        if (columnName == null) return null;
        Map<String, String> mapping = getPropertyMapping(entityClass);
        return mapping.getOrDefault(columnName, columnName);
    }
}
