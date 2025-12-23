package com.dream.codegenerate.utils.autoAudit;

import cn.hutool.v7.core.reflect.FieldUtil;
import cn.hutool.v7.core.text.StrUtil;
import cn.hutool.v7.core.util.ObjUtil;
import cn.hutool.v7.json.JSONUtil;
import com.dream.codegenerate.log.OperationContext;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SmartAuditUpdater - 实体-快照深度对比引擎 (极致性能优化版)
 * <p>
 * 核心优化：
 * 1. 自动映射 Action：默认映射为 新增/删除/修改，支持显式覆盖。
 * 2. 极致精简模式：开关关闭时，Create/Delete 零反射损耗，Update 零详情输出。
 * 3. 2S 补全逻辑：自动处理更新时间字段。
 * </p>
 */
@CustomLog
public class SmartAuditUpdater<S, T> {

    /**
     * 全量字段输出开关
     * false (默认): 仅输出业务摘要(summary)，details 列表为空。
     * true: 在日志中输出完整的字段级变更详情。
     */
    @Setter
    @Getter
    private static boolean fullJsonOnCreateDelete = false;

    private final S source;
    private final T target;
    private final List<AuditChange> auditChanges = new ArrayList<>();
    private final Set<String> manualIgnoreFields = new HashSet<>();
    private String customAction;
    private boolean isPartial = false;           // 是否为增量对比模式
    private boolean includeSystemFields = false; // 是否包含系统字段(如 update_time)

    private static final int MAX_DEPTH = 5;

    public record AuditChange(
            String fieldPath,
            String displayName,
            Object oldValue,
            Object newValue
    ) {
        public String getBusinessSummary() {
            String from = formatValue(oldValue);
            String to = formatValue(newValue);
            return String.format("【%s】从 [%s] 变为了 [%s]", displayName, from, to);
        }

        private String formatValue(Object val) {
            if (val == null) return "空";
            if (val instanceof Enum<?> e) {
                try {
                    Field[] fields = e.getDeclaringClass().getDeclaredFields();
                    if (fields.length > 0) {
                        Field firstField = fields[0];
                        firstField.setAccessible(true);
                        Object textValue = firstField.get(e);
                        return textValue != null ? textValue.toString() : e.name();
                    }
                } catch (Exception ignored) {}
                return e.name();
            }
            return (val instanceof String || val instanceof Number || val instanceof Boolean)
                    ? val.toString() : JSONUtil.toJsonStr(val);
        }
    }

    private SmartAuditUpdater(S source, T target) {
        this.source = source;
        this.target = target;
        this.manualIgnoreFields.add("serialVersionUID");
    }

    public static <S, T> SmartAuditUpdater<S, T> copy(S source, T target) {
        return new SmartAuditUpdater<>(source, target);
    }

    public SmartAuditUpdater<S, T> partial(boolean partial) {
        this.isPartial = partial;
        return this;
    }

    public SmartAuditUpdater<S, T> includeSystemFields(boolean include) {
        this.includeSystemFields = include;
        return this;
    }

    public SmartAuditUpdater<S, T> ignore(String... fields) {
        if (fields != null) Collections.addAll(this.manualIgnoreFields, fields);
        return this;
    }

    public SmartAuditUpdater<S, T> withAction(String action) {
        this.customAction = action;
        return this;
    }

    /**
     * 执行审计核心流程
     */
    public UpdateResult<T> execute() {
        // 1. 删除场景
        if (target == null && source != null) {
            return processCreateOrDelete(source, "Delete");
        }
        // 2. 新增场景
        if (source == null && target != null) {
            return processCreateOrDelete(target, "Create");
        }
        // 3. 更新场景
        if (source != null) {
            Class<?> targetClass = target.getClass();
            String bizRootName = AuditMetaCache.getDisplayName(targetClass);

            // 扫描差异以生成 summary
            compareDeep("", bizRootName, source, target, targetClass, 0);

            if (!auditChanges.isEmpty()) {
                String summary = auditChanges.stream()
                        .map(AuditChange::getBusinessSummary)
                        .collect(Collectors.joining(" | "));

                // 根据开关决定日志中是否包含详细差异列表
                List<AuditChange> logDetails = fullJsonOnCreateDelete ? auditChanges : Collections.emptyList();
                UpdateResult<T> logResult = new UpdateResult<>(target, logDetails);

                writeAuditLog(bizRootName, logResult, "Update", summary);
                // 返回值保留真实差异，仅日志输出时精简
                return new UpdateResult<>(target, auditChanges);
            }
        }
        return new UpdateResult<>(target, Collections.emptyList());
    }

    /**
     * 处理新增/删除逻辑（带性能短路）
     */
    private UpdateResult<T> processCreateOrDelete(Object entity, String defaultType) {
        Class<?> clazz = entity.getClass();
        String entityDisplayName = AuditMetaCache.getDisplayName(clazz);
        String summary;
        List<AuditChange> diffsForLog = Collections.emptyList();

        if (fullJsonOnCreateDelete) {
            // 全量模式：扫描所有字段
            if ("Create".equals(defaultType)) compareDeep("", entityDisplayName, null, entity, clazz, 0);
            else compareDeep("", entityDisplayName, entity, null, clazz, 0);

            summary = auditChanges.stream()
                    .map(AuditChange::getBusinessSummary)
                    .collect(Collectors.joining(" | "));
            diffsForLog = this.auditChanges;
        } else {
            // 精简模式：直接短路，不走反射对比逻辑
            String actionName = translateAction(defaultType);
            summary = String.format("%s【%s】", actionName, entityDisplayName);
        }

        UpdateResult<T> result = new UpdateResult<>(target, diffsForLog);
        writeAuditLog(entityDisplayName, result, defaultType, summary);
        return result;
    }

    private void compareDeep(String path, String displayName, Object sVal, Object tVal, Class<?> clazz, int depth) {
        if (isPartial && tVal == null) return;
        if (depth > MAX_DEPTH || (sVal != null && tVal != null && ObjUtil.equals(sVal, tVal))) return;
        if (sVal == null && tVal == null) return;

        Class<?> activeClass = tVal != null ? tVal.getClass() : sVal.getClass();
        if (isSimpleType(activeClass)) {
            auditChanges.add(new AuditChange(path, displayName, sVal, tVal));
            return;
        }

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
                || clazz == Boolean.class || clazz == Character.class || Date.class.isAssignableFrom(clazz) || clazz.isEnum()
                || clazz == LocalDateTime.class;
    }

    private void compareBean(String path, String displayName, Object sBean, Object tBean, int depth) {
        Class<?> beanClass = (tBean != null) ? tBean.getClass() : sBean.getClass();
        Field[] fields = FieldUtil.getFields(beanClass);

        for (Field field : fields) {
            if (shouldIgnoreField(field, depth)) continue;

            Object tValue = (tBean != null) ? FieldUtil.getFieldValue(tBean, field) : null;

            // 自动补全更新时间
            if (includeSystemFields && isPartial && tValue == null) {
                Column column = field.getAnnotation(Column.class);
                if (column != null && StrUtil.isNotEmpty(column.onUpdateValue())) {
                    tValue = LocalDateTime.now();
                }
            }

            if (isPartial && tValue == null) continue;

            Object sValue = (sBean != null) ? FieldUtil.getFieldValue(sBean, field) : null;
            String fieldBizName = AuditMetaCache.getDisplayName(field);
            String currentPath = StrUtil.isEmpty(path) ? field.getName() : path + "." + field.getName();
            String currentBizName = StrUtil.isEmpty(displayName) ? fieldBizName : (depth == 0 ? fieldBizName : displayName + "." + fieldBizName);

            compareDeep(currentPath, currentBizName, sValue, tValue, field.getType(), depth + 1);
        }
    }

    private boolean shouldIgnoreField(Field field, int depth) {
        String fieldName = field.getName();
        if (depth == 0 && manualIgnoreFields.contains(fieldName)) return true;

        Column column = field.getAnnotation(Column.class);
        if (column != null) {
            if (column.ignore()) return true;
            if (StrUtil.isNotEmpty(column.onInsertValue()) && !includeSystemFields) return true;
            if (StrUtil.isNotEmpty(column.onUpdateValue()) && !includeSystemFields) return true;
            if (column.isLogicDelete()) return true;
        }

        return !isPartial && field.isAnnotationPresent(Id.class);
    }

    private void compareMap(String path, String displayName, Map<?, ?> sMap, Map<?, ?> tMap, int depth) {
        Set<Object> keys = new HashSet<>();
        if (sMap != null) keys.addAll(sMap.keySet());
        if (tMap != null) keys.addAll(tMap.keySet());
        for (Object key : keys) {
            Object tVal = (tMap != null) ? tMap.get(key) : null;
            if (isPartial && tVal == null) continue;
            String k = String.valueOf(key);
            compareDeep(path + "{\"" + k + "\"}", displayName + "[" + k + "]",
                    sMap != null ? sMap.get(key) : null, tVal, Object.class, depth + 1);
        }
    }

    private void compareCollection(String path, String displayName, Collection<?> sColl, Collection<?> tColl, int depth) {
        Object[] sArr = sColl != null ? sColl.toArray() : new Object[0];
        Object[] tArr = tColl != null ? tColl.toArray() : new Object[0];
        int maxLen = Math.max(sArr.length, tArr.length);
        for (int i = 0; i < maxLen; i++) {
            Object tVal = i < tArr.length ? tArr[i] : null;
            if (isPartial && tVal == null) continue;
            compareDeep(path + "[" + i + "]", displayName + " 第" + (i + 1) + "项",
                    i < sArr.length ? sArr[i] : null, tVal, Object.class, depth + 1);
        }
    }

    /**
     * 写入审计日志，处理动作名称映射和详情过滤
     */
    private void writeAuditLog(String entityName, UpdateResult<T> result, String defaultType, String summary) {
        OperationContext.OperationInfo ctx = OperationContext.get();
        Object id = null;
        try {
            if (target != null) id = FieldUtil.getFieldValue(target, "id");
            if (id == null && source != null) id = FieldUtil.getFieldValue(source, "id");
        } catch (Exception ignored) {}

        // 动作名称逻辑：显式指定的非空 customAction 优先 > 引擎内置翻译
        String finalAction = StrUtil.isNotBlank(customAction) ? customAction : translateAction(defaultType);

        Map<String, Object> logPayload = new LinkedHashMap<>();
        logPayload.put("traceId", ctx != null ? ctx.getTraceId() : "INTERNAL");
        logPayload.put("operatorName", ctx != null ? ctx.getOperatorName() : "SYSTEM");
        logPayload.put("action", finalAction);
        logPayload.put("entity", entityName);
        logPayload.put("entityId", id);
        logPayload.put("summary", summary);
        logPayload.put("details", result.diffs()); // 已根据开关过滤

        log.info("PRECISION_AUDIT: {}", JSONUtil.toJsonStr(logPayload));
    }

    private String translateAction(String type) {
        if (type == null) return "未知操作";
        return switch (type) {
            case "Create" -> "新增";
            case "Delete" -> "删除";
            case "Update" -> "修改";
            default -> type;
        };
    }

    public record UpdateResult<T>(T entity, List<AuditChange> diffs) {
        public boolean isChanged() { return !diffs.isEmpty(); }
    }
}
