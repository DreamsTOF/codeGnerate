package com.dream.codegenerate.utils.autoAudit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/**
 * AuditMetaCache - 审计元数据缓存中心
 * 职责：缓存 Java 类和字段与业务显示名 (@Schema/@ApiModelProperty) 的映射关系。
 */
public class AuditMetaCache {

    private static final Cache<AnnotatedElement, String> DISPLAY_NAME_CACHE = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterAccess(24, TimeUnit.HOURS)
            .build();

    /**
     * 获取字段的业务显示名称
     */
    public static String getDisplayName(Field field) {
        return DISPLAY_NAME_CACHE.get(field, AuditMetaCache::resolveDisplayNameFromAnnotations);
    }

    /**
     * 获取类的业务显示名称
     */
    public static String getDisplayName(Class<?> clazz) {
        return DISPLAY_NAME_CACHE.get(clazz, AuditMetaCache::resolveDisplayNameFromAnnotations);
    }

    private static String resolveDisplayNameFromAnnotations(AnnotatedElement element) {
        // 1. 尝试 OpenAPI 3 (@Schema)
        try {
            Class<?> schemaClass = Class.forName("io.swagger.v3.oas.annotations.media.Schema");
            Object schema = element.getAnnotation((Class) schemaClass);
            if (schema != null) {
                String desc = (String) schemaClass.getMethod("description").invoke(schema);
                if (!desc.isEmpty()) return desc;
            }
        } catch (Exception ignored) {}

        // 2. 尝试 Swagger 2 (@ApiModel 或 @ApiModelProperty)
        try {
            // 针对类
            if (element instanceof Class) {
                Class<?> apiModelClass = Class.forName("io.swagger.annotations.ApiModel");
                Object apiModel = element.getAnnotation((Class) apiModelClass);
                if (apiModel != null) {
                    String value = (String) apiModelClass.getMethod("value").invoke(apiModel);
                    if (!value.isEmpty()) return value;
                }
            }
            // 针对字段
            Class<?> apiPropClass = Class.forName("io.swagger.annotations.ApiModelProperty");
            Object apiProp = element.getAnnotation((Class) apiPropClass);
            if (apiProp != null) {
                String value = (String) apiPropClass.getMethod("value").invoke(apiProp);
                if (!value.isEmpty()) return value;
            }
        } catch (Exception ignored) {}

        // 兜底策略
        if (element instanceof Class<?> clazz) return clazz.getSimpleName();
        if (element instanceof Field field) return field.getName();
        return "Unknown";
    }
}
