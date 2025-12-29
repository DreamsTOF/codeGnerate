package com.dream.codegenerate.utils.smartQuery;

import com.dream.codegenerate.mapper.UniversalSearchMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能查询上下文：管理全局配置、缓存与反射元数据
 */
public class SmartQueryContext {

    private static volatile UniversalSearchMapper searchMapper;
    private static volatile ObjectMapper objectMapper;
    private static final Map<Class<?>, TableInfo> TABLE_CACHE = new ConcurrentHashMap<>();

    // 元数据记录
    public record VoFieldMeta(String name, Class<?> type, boolean isCollection, Class<?> componentType, SmartFetch smartFetch, Relation relation) {}
    public record DtoFieldMeta(String name, Class<?> type, QueryMapping mapping) {}

    // Caffeine 缓存
    private static final Cache<Class<?>, List<VoFieldMeta>> VO_META_CACHE = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofHours(24)).build();
    private static final Cache<Class<?>, Map<String, DtoFieldMeta>> DTO_META_CACHE = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofHours(24)).build();

    // 【新增】列名 -> 属性名 反向映射缓存 (用于延迟加载时通过 column 找 property)
    private static final Cache<Class<?>, Map<String, String>> COL_PROP_CACHE = Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofHours(24)).build();

    public static void init(UniversalSearchMapper m, ObjectMapper om) {
        searchMapper = m;
        objectMapper = om;
    }

    public static UniversalSearchMapper getMapper() { return searchMapper; }
    public static ObjectMapper getObjectMapper() { return objectMapper; }

    public static TableInfo getTableInfo(Class<?> entityClass) {
        return TABLE_CACHE.computeIfAbsent(entityClass, TableInfoFactory::ofEntityClass);
    }

    /**
     * 【新增】通过数据库列名反查 Entity 属性名
     */
    public static String getPropertyByColumn(Class<?> entityClass, String colName) {
        if (colName == null) return null;
        return COL_PROP_CACHE.get(entityClass, c -> {
            Map<String, String> map = new HashMap<>();
            TableInfo info = getTableInfo(c);
            // 遍历所有字段，建立 Column -> Property 映射
            List<Field> fields = getAllFields(c);
            for (Field f : fields) {
                String col = info.getColumnByProperty(f.getName());
                if (col != null) {
                    map.put(col, f.getName());
                }
            }
            return map;
        }).get(colName);
    }

    public static List<VoFieldMeta> getVoFields(Class<?> clazz) {
        return VO_META_CACHE.get(clazz, c -> {
            List<VoFieldMeta> metas = new ArrayList<>();
            List<Field> allFields = getAllFields(c);
            for (Field f : allFields) {
                // 【修复核心】忽略 static 字段 (如 serialVersionUID)
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                boolean isCol = Collection.class.isAssignableFrom(f.getType());
                Class<?> compType = f.getType();
                if (isCol) {
                    Type gt = f.getGenericType();
                    if (gt instanceof ParameterizedType pt) compType = (Class<?>) pt.getActualTypeArguments()[0];
                }
                metas.add(new VoFieldMeta(f.getName(), f.getType(), isCol, compType, f.getAnnotation(SmartFetch.class), f.getAnnotation(Relation.class)));
            }
            return metas;
        });
    }

    public static Map<String, DtoFieldMeta> getDtoFields(Class<?> clazz) {
        return DTO_META_CACHE.get(clazz, c -> {
            Map<String, DtoFieldMeta> map = new HashMap<>();
            Class<?> temp = c;
            while (temp != null && temp != Object.class) {
                for (Field f : temp.getDeclaredFields()) {
                    map.put(f.getName(), new DtoFieldMeta(f.getName(), f.getType(), f.getAnnotation(QueryMapping.class)));
                }
                temp = temp.getSuperclass();
            }
            return map;
        });
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> temp = clazz;
        while (temp != null && temp != Object.class) {
            Collections.addAll(fields, temp.getDeclaredFields());
            temp = temp.getSuperclass();
        }
        return fields;
    }

    @Configuration
    public static class SpringAutoConfig {

        public SpringAutoConfig(UniversalSearchMapper mapper, ObjectMapper objectMapper) {
            SmartQueryContext.init(mapper, objectMapper);
        }
//        @PostConstruct public void setup() { SmartQueryContext.init(mapper, objectMapper); }
    }
}
