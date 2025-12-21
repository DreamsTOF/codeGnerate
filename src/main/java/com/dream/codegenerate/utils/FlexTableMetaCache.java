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
 * 🚀 FlexTableMetaCache - MyBatis-Flex 元数据深度缓存
 * ==================================================================================
 * * 🛠 【为什么要引入缓存?】:
 * ----------------------------------------------------------------------------------
 * 在处理大批量数据回填时，我们需要频繁地根据数据库“列名”反查 Java “属性名”。
 * MyBatis-Flex 的 TableInfo 虽然提供了这些映射，但每次查找都伴随着 Map 遍历或
 * 反射操作。在万级循环中，这会导致明显的延迟。
 * * 🧩 【设计方案】:
 * ----------------------------------------------------------------------------------
 * 采用 Google Caffeine 高性能缓存框架，将每一个 Entity 类的列名/属性名映射全量
 * 缓存在内存中。
 * * 📝 【主要功能】:
 * ----------------------------------------------------------------------------------
 * 1. 自动注入：首次访问时自动扫描 TableInfo 并加载。
 * 2. 完备性：同时包含普通字段映射与主键字段映射。
 * 3. 容错性：查找失败时默认返回原始名称。
 * ==================================================================================
 */
public class FlexTableMetaCache {

    /**
     * 映射缓存容器。
     * 配置策略：最大 1000 个实体类（覆盖绝大部分项目），
     * 如果一个实体类 24 小时未被查询，则从内存中释放。
     */
    private static final Cache<Class<?>, Map<String, String>> MAPPING_POOL = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(24, TimeUnit.HOURS)
            .build();

    /**
     * 获取指定实体的全量字段映射表。
     * * @param entityClass 实体类类型
     * @return 映射关系 (Key: 数据库列名, Value: Java 属性名)
     */
    public static Map<String, String> getPropertyMapping(Class<?> entityClass) {
        return MAPPING_POOL.get(entityClass, clazz -> {
            TableInfo tableInfo = TableInfoFactory.ofEntityClass(clazz);
            Map<String, String> map = new HashMap<>();

            // 1. 扫描普通字段信息
            Optional.ofNullable(tableInfo.getColumnInfoList())
                    .ifPresent(list -> list.forEach(i -> map.put(i.getColumn(), i.getProperty())));

            // 2. 扫描主键字段信息 (主键也是重要的列)
            Optional.ofNullable(tableInfo.getPrimaryKeyList())
                    .ifPresent(list -> list.forEach(pk -> map.put(pk.getColumn(), pk.getProperty())));

            // 使用不可变 Map 包装，提升读取性能并保证线程安全
            return Map.copyOf(map);
        });
    }

    /**
     * 根据数据库物理列名查找 Java 属性名。
     * 该方法是 FlexSmartQuery 执行回填任务时的核心依赖。
     * * @param entityClass 宿主类
     * @param columnName  物理列名 (如: user_id)
     * @return 属性名 (如: userId)
     */
    public static String getPropertyName(Class<?> entityClass, String columnName) {
        if (columnName == null) return null;
        Map<String, String> mapping = getPropertyMapping(entityClass);
        // 查找映射，如果不存在则直接返回原名，增加鲁棒性
        return mapping.getOrDefault(columnName, columnName);
    }
}
