package com.dream.codegenerate.utils.autoAudit;

import cn.hutool.v7.core.bean.BeanUtil;
import cn.hutool.v7.core.reflect.FieldUtil;
import cn.hutool.v7.core.text.StrUtil;
import cn.hutool.v7.core.util.ObjUtil;
import cn.hutool.v7.json.JSONUtil;
import com.dream.codegenerate.log.OperationContext;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SmartAuditUpdater - 实体-快照深度对比引擎 (DeepDiffEngine)
 * 核心逻辑：
 * 1. 类名语义化：自动抓取类上的 @Schema(description="产品")。
 * 2. 增删改全覆盖：支持通过全局开关控制增删时的日志详略。
 */
@CustomLog
public class SmartAuditUpdater<S, T> {

    @Setter
    @Getter
    private static boolean fullJsonOnCreateDelete = false;

    private final S source;
    private final T target;
    private final List<AuditChange> auditChanges = new ArrayList<>();
    private final Set<String> ignoreFields = new HashSet<>();
    private String customAction;

    private static final int MAX_DEPTH = 5;

    public record AuditChange(
            String fieldPath,
            String displayName,
            Object oldValue,
            Object newValue
    ) {
        public String getBusinessSummary() {
            String from = JSONUtil.toJsonStr(oldValue);
            String to = JSONUtil.toJsonStr(newValue);
            return String.format("【%s】从 [%s] 变为了 [%s]", displayName, from, to);
        }
    }

    private SmartAuditUpdater(S source, T target) {
        this.source = source;
        this.target = target;
        this.ignoreFields.addAll(List.of("id", "createTime", "updateTime", "isDeleted", "serialVersionUID", "updateBy", "createBy"));
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
        if (target == null && source != null) {
            return processCreateOrDelete(source, "Delete");
        }
        if (source == null && target != null) {
            return processCreateOrDelete(target, "Create");
        }
        if (source != null && target != null) {
            Class<?> targetClass = target.getClass();
            // 修改操作也使用业务名称作为根路径起点
            String bizRootName = AuditMetaCache.getDisplayName(targetClass);
            compareDeep("", bizRootName, source, target, targetClass, 0);
            UpdateResult<T> result = new UpdateResult<>(target, auditChanges);
            if (result.isChanged()) {
                writeAuditLog(bizRootName, result, "Update");
            }
            return result;
        }
        return new UpdateResult<>(target, Collections.emptyList());
    }

    private UpdateResult<T> processCreateOrDelete(Object entity, String defaultAction) {
        Class<?> clazz = entity.getClass();

        // 核心改动：从类注解中获取业务名（如 "产品"）
        String entityDisplayName = AuditMetaCache.getDisplayName(clazz);
        String summary;

        if (fullJsonOnCreateDelete) {
            if ("Create".equals(defaultAction)) {
                compareDeep("", entityDisplayName, null, entity, clazz, 0);
            } else {
                compareDeep("", entityDisplayName, entity, null, clazz, 0);
            }
            summary = auditChanges.stream().map(AuditChange::getBusinessSummary).collect(Collectors.joining(" | "));
        } else {
            // 输出你想要的：新增【产品】
            String actionName = "Create".equals(defaultAction) ? "新增" : "删除";
            summary = String.format("%s【%s】", actionName, entityDisplayName);
        }

        UpdateResult<T> result = new UpdateResult<>(target, auditChanges);
        writeAuditLog(entityDisplayName, result, defaultAction, summary);
        return result;
    }

    private void compareDeep(String path, String displayName, Object sVal, Object tVal, Class<?> clazz, int depth) {
        if (depth > MAX_DEPTH || (sVal != null && tVal != null && ObjUtil.equals(sVal, tVal))) return;
        if (sVal == null && tVal == null) return;

        Class<?> activeClass = (tVal != null) ? tVal.getClass() : (sVal != null ? sVal.getClass() : null);
        if (activeClass == null || isSimpleType(activeClass)) {
            auditChanges.add(new AuditChange(path, displayName, sVal, tVal));
            return;
        }

        // 修复：将 sColl/tColl 改为当前作用域变量名 sVal/tVal
        if (sVal instanceof Map<?, ?> || tVal instanceof Map<?, ?>) {
            compareMap(path, displayName, (Map) sVal, (Map) tVal, depth);
            return;
        }

        if (sVal instanceof Collection<?> || tVal instanceof Collection<?>) {
            compareCollection(path, displayName, (Collection) sVal, (Collection) tVal, depth);
            return;
        }

        compareBean(path, displayName, sVal, tVal, depth);
    }

    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() || Number.class.isAssignableFrom(clazz) || clazz == String.class
                || clazz == Boolean.class || clazz == Character.class || Date.class.isAssignableFrom(clazz) || clazz.isEnum();
    }

    private void compareBean(String path, String displayName, Object sBean, Object tBean, int depth) {
        Object activeBean = (tBean != null) ? tBean : sBean;
        if (activeBean == null) return;

        Map<String, Object> fieldsMap = BeanUtil.beanToMap(activeBean, false, false);
        Class<?> beanClass = activeBean.getClass();

        fieldsMap.forEach((fieldName, value) -> {
            if (depth == 0 && ignoreFields.contains(fieldName)) return;
            try {
                Field field = FieldUtil.getField(beanClass, fieldName);
                if (field == null) return;

                Object sValue = (sBean != null) ? FieldUtil.getFieldValue(sBean, fieldName) : null;
                Object tValue = (tBean != null) ? FieldUtil.getFieldValue(tBean, fieldName) : null;

                String fieldBizName = AuditMetaCache.getDisplayName(field);
                String currentPath = StrUtil.isEmpty(path) ? fieldName : path + "." + fieldName;
                String currentBizName = StrUtil.isEmpty(displayName) ? fieldBizName : displayName + "." + fieldBizName;

                compareDeep(currentPath, currentBizName, sValue, tValue, field.getType(), depth + 1);
            } catch (Exception ignored) {}
        });
    }

    private void compareMap(String path, String displayName, Map<?, ?> sMap, Map<?, ?> tMap, int depth) {
        Set<Object> keys = new HashSet<>();
        if (sMap != null) keys.addAll(sMap.keySet());
        if (tMap != null) keys.addAll(tMap.keySet());
        for (Object key : keys) {
            String k = String.valueOf(key);
            compareDeep(path + "{\"" + k + "\"}", displayName + "[" + k + "]",
                    sMap != null ? sMap.get(key) : null, tMap != null ? tMap.get(key) : null, Object.class, depth + 1);
        }
    }

    private void compareCollection(String path, String displayName, Collection<?> sColl, Collection<?> tColl, int depth) {
        Object[] sArr = sColl != null ? sColl.toArray() : new Object[0];
        Object[] tArr = tColl != null ? tColl.toArray() : new Object[0];
        int maxLen = Math.max(sArr.length, tArr.length);
        for (int i = 0; i < maxLen; i++) {
            compareDeep(path + "[" + i + "]", displayName + " 第" + (i + 1) + "项",
                    i < sArr.length ? sArr[i] : null, i < tArr.length ? tArr[i] : null, Object.class, depth + 1);
        }
    }

    private void writeAuditLog(String entityName, UpdateResult<T> result, String defaultAction) {
        String summary = result.diffs().stream().map(AuditChange::getBusinessSummary).collect(Collectors.joining(" | "));
        writeAuditLog(entityName, result, defaultAction, summary);
    }

    private void writeAuditLog(String entityName, UpdateResult<T> result, String defaultAction, String summary) {
        OperationContext.OperationInfo ctx = OperationContext.get();
        Object id = target != null ? FieldUtil.getFieldValue(target, "id") : (source != null ? FieldUtil.getFieldValue(source, "id") : null);

        Map<String, Object> logPayload = new LinkedHashMap<>();
        logPayload.put("traceId", ctx != null ? ctx.getTraceId() : "INTERNAL");
        logPayload.put("operatorName", ctx != null ? ctx.getOperatorName() : "SYSTEM");
        logPayload.put("action", customAction != null ? customAction : defaultAction);
        logPayload.put("entity", entityName);
        logPayload.put("entityId", id);
        logPayload.put("summary", summary);
        logPayload.put("details", result.diffs());

        log.info("PRECISION_AUDIT: {}", JSONUtil.toJsonStr(logPayload));
    }

    @Getter
    public record UpdateResult<T>(T entity, List<AuditChange> diffs) {
        public boolean isChanged() { return !diffs.isEmpty(); }
    }
}
