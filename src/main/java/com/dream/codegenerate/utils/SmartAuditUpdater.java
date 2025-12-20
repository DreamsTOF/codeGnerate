package com.dream.codegenerate.utils;

import cn.hutool.v7.core.bean.BeanUtil;
import cn.hutool.v7.core.reflect.FieldUtil;
import cn.hutool.v7.core.util.ObjUtil;
import cn.hutool.v7.json.JSONUtil;
import com.dream.codegenerate.log.OperationContext;
import lombok.CustomLog;
import lombok.Getter;

import java.lang.reflect.Field;
import java.util.*;

/**
 * SmartAuditUpdater - 智能审计更新器
 * <p>
 * 核心优化：
 * 1. 自动兼容多种注解：支持 OpenAPI 3 (@Schema) 和 Swagger 2 (@ApiModelProperty)。
 * 2. 极致自动化：配合 MyBatis-Flex 代码生成器，无需手动维护任何业务名称。
 * 3. 异步审计解耦：自动集成 OperationContext 中的 Trace 和 User 状态。
 * </p>
 */
@CustomLog
public class SmartAuditUpdater<S, T> {

    private final S source;
    private final T target;
    private final List<AuditChange> auditChanges = new ArrayList<>();
    private final Set<String> ignoreFields = new HashSet<>();
    private String customAction;

    public record AuditChange(
            String fieldName,
            String displayName,
            Object oldValue,
            Object newValue
    ) {
        @Override
        public String toString() {
            return String.format("{\"field\":\"%s(%s)\",\"from\":%s,\"to\":%s}",
                    fieldName, displayName, JSONUtil.toJsonStr(oldValue), JSONUtil.toJsonStr(newValue));
        }
    }

    private SmartAuditUpdater(S source, T target) {
        this.source = source;
        this.target = target;
        this.ignoreFields.addAll(List.of("id", "createTime", "updateTime", "isDeleted", "serialVersionUID"));
    }

    public static <S, T> SmartAuditUpdater<S, T> copy(S source, T target) {
        return new SmartAuditUpdater<>(source, target);
    }

    public SmartAuditUpdater<S, T> ignore(String... fields) {
        if (fields != null) Collections.addAll(this.ignoreFields, fields);
        return this;
    }

    public SmartAuditUpdater<S, T> withAction(String action) {
        this.customAction = action;
        return this;
    }

    public UpdateResult<T> execute() {
        if (source == null || target == null) return new UpdateResult<>(target, Collections.emptyList());

        Map<String, Object> sourceMap = BeanUtil.beanToMap(source, false, true);
        Class<?> targetClass = target.getClass();

        sourceMap.forEach((fieldName, newValue) -> {
            if (ignoreFields.contains(fieldName)) return;

            try {
                Field field = FieldUtil.getField(targetClass, fieldName);
                if (field == null) return;

                Object oldValue = FieldUtil.getFieldValue(target, fieldName);
                if (isSame(oldValue, newValue)) return;

                // 核心：自动识别业务名称
                String displayName = resolveDisplayName(field);

                FieldUtil.setFieldValue(target, fieldName, newValue);
                auditChanges.add(new AuditChange(fieldName, displayName, oldValue, newValue));

            } catch (Exception e) {
                log.error("AuditUpdate 失败: {}.{}, 错误: {}", targetClass.getSimpleName(), fieldName, e.getMessage());
            }
        });

        UpdateResult<T> result = new UpdateResult<>(target, auditChanges);
        if (result.isChanged()) {
            writeAuditLog(targetClass.getSimpleName(), result);
        }
        return result;
    }

    /**
     * 多策略识别字段显示名：
     * 1. 尝试 OpenAPI 3 的 @Schema(description)
     * 2. 尝试 Swagger 2 的 @ApiModelProperty(value)
     * 3. 回退至字段名
     */
    private String resolveDisplayName(Field field) {
        // 策略1: OpenAPI 3
        try {
            Class<?> schemaClass = Class.forName("io.swagger.v3.oas.annotations.media.Schema");
            Object schema = field.getAnnotation((Class) schemaClass);
            if (schema != null) {
                String desc = (String) schemaClass.getMethod("description").invoke(schema);
                if (!desc.isEmpty()) return desc;
            }
        } catch (Exception ignored) {}

        // 策略2: Swagger 2 (ApiModelProperty)
        try {
            Class<?> apiModelPropertyClass = Class.forName("io.swagger.annotations.ApiModelProperty");
            Object apiProp = field.getAnnotation((Class) apiModelPropertyClass);
            if (apiProp != null) {
                String value = (String) apiModelPropertyClass.getMethod("value").invoke(apiProp);
                if (!value.isEmpty()) return value;
            }
        } catch (Exception ignored) {}

        return field.getName();
    }

    private void writeAuditLog(String entityName, UpdateResult<T> result) {
        OperationContext.OperationInfo ctx = OperationContext.get();
        Object id = FieldUtil.getFieldValue(target, "id");

        String actionName = customAction != null ? customAction : (ctx != null ? ctx.getMethodName() : "Update");

        Map<String, Object> logPayload = new LinkedHashMap<>();
        logPayload.put("action", actionName);
        logPayload.put("traceId", ctx != null ? ctx.getTraceId() : "INTERNAL");
        logPayload.put("operator", ctx != null ? ctx.getOperatorId() : "SYSTEM");
        logPayload.put("entity", entityName);
        logPayload.put("entityId", id);
        logPayload.put("changes", result.getDiffs());

        log.info("PRECISION_AUDIT_LOG: {}", JSONUtil.toJsonStr(logPayload));
    }

    private boolean isSame(Object oldVal, Object newVal) {
        if (oldVal == newVal) return true;
        if (ObjUtil.isBasicType(oldVal) || oldVal instanceof String) {
            return ObjUtil.equals(oldVal, newVal);
        }
        return false;
    }

    @Getter
    public static class UpdateResult<T> {
        private final T entity;
        private final List<AuditChange> diffs;
        public UpdateResult(T entity, List<AuditChange> diffs) {
            this.entity = entity;
            this.diffs = diffs;
        }
        public boolean isChanged() { return !diffs.isEmpty(); }
    }
}
