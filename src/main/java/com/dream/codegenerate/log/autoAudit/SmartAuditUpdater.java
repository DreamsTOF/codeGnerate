package com.dream.codegenerate.log.autoAudit;

import cn.hutool.v7.core.collection.CollUtil;
import cn.hutool.v7.core.reflect.FieldUtil;
import cn.hutool.v7.core.text.StrUtil;
import cn.hutool.v7.core.util.ObjUtil;
import cn.hutool.v7.json.JSONUtil;
import com.dream.codegenerate.log.OperationContext;
import com.dream.codegenerate.mapper.UniversalSearchMapper;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.core.datasource.DataSourceKey;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import jakarta.annotation.PostConstruct;
import lombok.CustomLog;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SmartAuditUpdater - 完美审计引擎
 * <p>
 * 更新记录：
 * 1. [架构升级] executeBatch 支持自动聚合日志：多个实体修改合并为 1 条日志，减少日志刷屏。
 * 2. [视觉优化] description 字段在批量模式下自动分行显示，清晰分隔不同实体。
 * 3. [数据增强] 批量模式下的 changes 列表自动附加 _entity/_id 标识。
 * </p>
 */
@CustomLog
public class SmartAuditUpdater<S, T> {

    private static UniversalSearchMapper searchMapper;

    // --- 实例配置 ---
    private final S source;
    private final T target;
    private boolean isPartial = false;
    private boolean skipNullFields = true;
    private boolean includeSystemFields = false;
    private final Set<String> manualIgnoreFields = new HashSet<>();

    // --- 运行时数据 ---
    private final List<Map<String, Object>> changes = new ArrayList<>();
    private Map<String, String> translationMap = Collections.emptyMap();

    @Component
    public static class SpringAutoConfig {
        @Autowired
        private UniversalSearchMapper mapper;
        @PostConstruct
        public void init() {
            SmartAuditUpdater.searchMapper = mapper;
        }
    }

    private SmartAuditUpdater(S source, T target) {
        this.source = source;
        this.target = target;
        this.manualIgnoreFields.add("serialVersionUID");
        this.manualIgnoreFields.add("updateTime");
        this.manualIgnoreFields.add("editTime");
        this.manualIgnoreFields.add("modifyTime");
        this.manualIgnoreFields.add("createTime");
    }

    public static <S, T> SmartAuditUpdater<S, T> copy(S source, T target) {
        return new SmartAuditUpdater<>(source, target);
    }

    public SmartAuditUpdater<S, T> partial(boolean partial) { this.isPartial = partial; return this; }
    public SmartAuditUpdater<S, T> skipNullFields(boolean skip) { this.skipNullFields = skip; return this; }
    public SmartAuditUpdater<S, T> includeSystemFields(boolean include) { this.includeSystemFields = include; return this; }
    public SmartAuditUpdater<S, T> ignore(String... fields) { if (fields != null) Collections.addAll(this.manualIgnoreFields, fields); return this; }

    /**
     * 批量执行审计
     * <p>
     * 逻辑升级：
     * 1. 计算所有 Updater 的变更结果。
     * 2. 如果只有 1 个有变更 -> 打印单条日志。
     * 3. 如果有多个有变更 -> 聚合为 1 条"批量操作"日志，description 换行分隔。
     * </p>
     */
    public static void executeBatch(String module, String action, List<SmartAuditUpdater<?, ?>> updaters, long cost) {
        if (CollUtil.isEmpty(updaters)) return;

        // 1. 预取翻译
        Map<String, String> globalTranslations;
        try {
            globalTranslations = preFetchTranslations(updaters);
        } catch (Exception e) {
            log.warn("Audit Translation Failed: {}", e.toString());
            globalTranslations = Collections.emptyMap();
        }

        OperationContext.OperationInfo ctx = OperationContext.get();
        List<AuditResult> results = new ArrayList<>();

        // 2. 计算变更
        for (var updater : updaters) {
            updater.translationMap = globalTranslations;
            AuditResult result = updater.compare(action); // 计算但不打印
            if (result != null) {
                results.add(result);
            }
        }

        if (results.isEmpty()) return;

        // 3. 智能输出 (单条 vs 聚合)
        if (results.size() == 1) {
            logSingleAudit(ctx, module, action, cost, results.get(0));
        } else {
            logBatchAudit(ctx, module, action, cost, results);
        }
    }

    // --- 内部数据结构 ---
    @Data
    private static class AuditResult {
        String entity;
        Object entityId;
        String description;
        List<Map<String, Object>> changes;
        String operation; // CREATE/UPDATE/DELETE
    }

    // --- 核心比较逻辑 (提取自原 doCompareAndLog) ---
    private AuditResult compare(String action) {
        Object activeObj = target != null ? target : source;
        if (activeObj == null) return null;
        Class<?> clazz = activeObj.getClass();

        compareDeep("", AuditMetaCache.getDisplayName(clazz), source, target, clazz, 0);

        boolean hasChanges = !changes.isEmpty();
        boolean isCreateOrDelete = (source == null || target == null);

        if (!hasChanges && (!isCreateOrDelete || isPartial)) return null;

        String description = buildHumanReadableDescription(action, clazz, activeObj, changes);
        String operation = target == null ? "DELETE" : (source == null ? "CREATE" : "UPDATE");

        AuditResult res = new AuditResult();
        res.entity = clazz.getSimpleName();
        res.entityId = extractId(activeObj);
        res.description = description;
        res.changes = changes;
        res.operation = operation;
        return res;
    }

    // --- 日志输出实现 ---

    private static void logSingleAudit(OperationContext.OperationInfo ctx, String module, String action, long cost, AuditResult res) {
        Map<String, Object> logPacket = buildBaseLogPacket(ctx, module, action, cost);
        logPacket.put("entity", res.entity);
        logPacket.put("entityId", res.entityId);
        logPacket.put("operation", res.operation);
        logPacket.put("description", res.description);
        logPacket.put("changes", res.changes);

        log.info("AUDIT_LOG: {}", JSONUtil.toJsonStr(logPacket));
    }

    private static void logBatchAudit(OperationContext.OperationInfo ctx, String module, String action, long cost, List<AuditResult> results) {
        Map<String, Object> logPacket = buildBaseLogPacket(ctx, module, action, cost);

        // 聚合信息
        logPacket.put("entity", "Batch(" + results.stream().map(AuditResult::getEntity).distinct().collect(Collectors.joining(",")) + ")");
        logPacket.put("entityId", "MULTIPLE"); // 批量操作不记录单一ID
        logPacket.put("operation", "BATCH");

        // 1. 聚合 Description (使用换行符分隔，实现"明确的分隔")
        StringBuilder descBuilder = new StringBuilder();
        // 如果 action 为空，给个默认标题
        String title = StrUtil.isNotBlank(action) ? action : "批量操作";
        descBuilder.append(title).append(" (共").append(results.size()).append("项变更)：\n");

        List<Map<String, Object>> allChanges = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            AuditResult res = results.get(i);
            // 拼接描述： (1) 更新用户(ID:1): ...
            descBuilder.append("(").append(i + 1).append(") ").append(res.description);
            if (i < results.size() - 1) {
                descBuilder.append("\n"); // 换行分隔
            }

            // 聚合 Changes，并注入实体身份
            for (Map<String, Object> change : res.changes) {
                // 浅拷贝一份，防止污染原始数据 (虽然这里是一次性的)
                Map<String, Object> enrichedChange = new LinkedHashMap<>(change);
                enrichedChange.put("_entity", res.entity); // 增加实体标识
                enrichedChange.put("_id", res.entityId);   // 增加ID标识
                allChanges.add(enrichedChange);
            }
        }

        logPacket.put("description", descBuilder.toString());
        logPacket.put("changes", allChanges);

        log.info("AUDIT_LOG: {}", JSONUtil.toJsonStr(logPacket));
    }

    private static Map<String, Object> buildBaseLogPacket(OperationContext.OperationInfo ctx, String module, String action, long cost) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logType", "AUDIT_SUCCESS");
        m.put("traceId", ctx.getTraceId());
        m.put("isDefault", ctx.isDefault());
        m.put("operatorId", ctx.getOperatorId());
        m.put("operatorName", ctx.getOperatorName());
        m.put("module", StrUtil.isNotBlank(module) ? module : ctx.getBusinessModule());
        m.put("action", StrUtil.isNotBlank(action) ? action : ctx.getBusinessAction());
        m.put("costTime", cost);
        m.put("timestamp", System.currentTimeMillis());
        return m;
    }

    // --- 辅助方法 (保持不变) ---

    private String buildHumanReadableDescription(String action, Class<?> clazz, Object activeObj, List<Map<String, Object>> changes) {
        StringBuilder desc = new StringBuilder();

        String opType = target == null ? "删除" : (source == null ? "创建" : "更新");
        // 注意：在批量模式下，action 通常是"更新组织架构"这种大词，而这里是单个实体的描述
        // 所以这里我们只用 opType + EntityName 即可，避免重复 action
        // 但为了兼容单条模式，如果外部没传 action，我们自己生成
        String entityName = AuditMetaCache.getDisplayName(clazz);
        desc.append(opType).append(entityName);

        Object id = extractId(activeObj);
        if (id != null) {
            desc.append("(ID:").append(id).append(")");
        }
        desc.append("：");

        if (changes.isEmpty()) {
            desc.append("无内容变更");
        } else {
            String details = changes.stream().map(c -> {
                String fieldName = (String) c.get("name");
                Object oldVal = c.get("old");
                Object newVal = c.get("new");

                if (source == null) return String.format("【%s】设置为“%s”", fieldName, newVal);
                if (target == null) return String.format("【%s】值为“%s”", fieldName, oldVal);
                if (oldVal == null) return String.format("【%s】设置为“%s”", fieldName, newVal);
                if (newVal == null) return String.format("【%s】被置空(原值“%s”)", fieldName, oldVal);

                return String.format("【%s】从“%s”改为“%s”", fieldName, oldVal, newVal);
            }).collect(Collectors.joining("；"));

            desc.append(details);
        }
        return desc.toString();
    }

    private void compareDeep(String path, String displayName, Object sVal, Object tVal, Class<?> clazz, int depth) {
        if (depth > 5) return;
        if (skipNullFields && tVal == null && depth > 0) return;
        if (ObjUtil.equals(sVal, tVal)) return;

        if (Collection.class.isAssignableFrom(clazz)) {
            int sSize = sVal instanceof Collection<?> c ? c.size() : 0;
            int tSize = tVal instanceof Collection<?> c ? c.size() : 0;
            if (sSize != tSize) recordChange(path, displayName, "Size:" + sSize, "Size:" + tSize);
            return;
        }

        if (isSimpleType(clazz)) {
            recordChange(path, displayName, sVal, tVal);
            return;
        }

        Field[] fields = FieldUtil.getFields(clazz);
        for (Field field : fields) {
            if (shouldIgnoreField(field)) continue;
            Object sv = sVal != null ? FieldUtil.getFieldValue(sVal, field) : null;
            Object tv = tVal != null ? FieldUtil.getFieldValue(tVal, field) : null;

            if (skipNullFields && tv == null) continue;

            String curPath = StrUtil.isEmpty(path) ? field.getName() : path + "." + field.getName();
            String curName = AuditMetaCache.getDisplayName(field);

            if (!ObjUtil.equals(sv, tv)) {
                if (isSimpleType(field.getType())) {
                    Object oldDisplay = tryLookupTranslation(sv, field);
                    Object newDisplay = tryLookupTranslation(tv, field);
                    recordChange(curPath, curName, oldDisplay, newDisplay);
                } else {
                    compareDeep(curPath, curName, sv, tv, field.getType(), depth + 1);
                }
            }
        }
    }

    private Object tryLookupTranslation(Object value, Field field) {
        if (value == null) return null;
        AuditReference ref = field.getAnnotation(AuditReference.class);
        if (ref == null || ref.target() == void.class) return value;
        String key = String.format("%s:%s:%s:%s", ref.target().getName(), ref.label(), ref.idCol(), value);
        return translationMap.getOrDefault(key, value + "(未知)");
    }

    private static Map<String, String> preFetchTranslations(List<SmartAuditUpdater<?, ?>> updaters) {
        if (searchMapper == null) return Collections.emptyMap();
        Map<String, Map<String, Set<Object>>> dsRequestMap = new HashMap<>();

        for (var updater : updaters) {
            Object target = updater.target != null ? updater.target : updater.source;
            if (target == null) continue;
            for (Field field : FieldUtil.getFields(target.getClass())) {
                AuditReference ref = field.getAnnotation(AuditReference.class);
                if (ref != null && ref.target() != void.class) {
                    Object v1 = updater.source != null ? FieldUtil.getFieldValue(updater.source, field) : null;
                    Object v2 = updater.target != null ? FieldUtil.getFieldValue(updater.target, field) : null;
                    if (v1 == null && v2 == null) continue;

                    TableInfo targetInfo = TableInfoFactory.ofEntityClass(ref.target());
                    if (targetInfo == null) continue;

                    String dsKey = targetInfo.getDataSource();
                    String key = String.format("%s:%s:%s", ref.target().getName(), ref.label(), ref.idCol());
                    dsRequestMap.computeIfAbsent(dsKey, k -> new HashMap<>())
                            .computeIfAbsent(key, x -> new HashSet<>())
                            .addAll(Arrays.asList(v1, v2));
                }
            }
        }

        if (dsRequestMap.isEmpty()) return Collections.emptyMap();
        Map<String, String> finalResults = new ConcurrentHashMap<>();

        dsRequestMap.forEach((dsKey, requestMap) -> {
            if (requestMap.isEmpty()) return;
            StringBuilder sql = new StringBuilder();
            int idx = 0;

            for (var entry : requestMap.entrySet()) {
                String[] parts = entry.getKey().split(":");
                Class<?> targetClass = null;
                try { targetClass = Class.forName(parts[0]); } catch (Exception ignored) {}
                if (targetClass == null) continue;

                TableInfo info = TableInfoFactory.ofEntityClass(targetClass);
                String propertyOrCol = parts[1];
                String idCol = parts[2];
                String labelCol = info.getColumnByProperty(propertyOrCol);
                if (StrUtil.isBlank(labelCol)) labelCol = propertyOrCol;
                String tableName = info.getTableName();
                String ids = entry.getValue().stream().filter(Objects::nonNull)
                        .map(AuditDialectSupport::sqlEscape).collect(Collectors.joining(","));

                if (StrUtil.isNotBlank(labelCol) && StrUtil.isNotBlank(ids)) {
                    sql.append(String.format("SELECT '%s' as _g, CAST(%s AS CHAR) as _i, CAST(%s AS CHAR) as _v FROM %s WHERE %s IN (%s)",
                            entry.getKey(), idCol, labelCol, tableName, idCol, ids));
                    if (++idx < requestMap.size()) sql.append(" UNION ALL ");
                }
            }

            String finalSql = sql.toString();
            if (finalSql.endsWith(" UNION ALL ")) finalSql = finalSql.substring(0, finalSql.length() - 11);

            if (!finalSql.isEmpty()) {
                String finalSql1 = finalSql;
                try {
                    if (dsKey != null) DataSourceKey.use(dsKey);
                    else try { DataSourceKey.clear(); } catch (Exception ignored) {}

                    var rows = searchMapper.executeDynamicUnionQuery(finalSql1);
                    if (rows != null) {
                        for (var row : rows) finalResults.put(row.get("_g") + ":" + row.get("_i"), String.valueOf(row.get("_v")));
                    }
                } catch (Exception e) {
                    log.warn("AutoAudit DS:{} Query Error: {}", dsKey, e.toString());
                } finally {
                    try { DataSourceKey.clear(); } catch (Exception ignored) {}
                }
            }
        });

        return finalResults;
    }

    private boolean shouldIgnoreField(Field field) {
        if (manualIgnoreFields.contains(field.getName())) return true;
        Column column = field.getAnnotation(Column.class);
        if (column != null) {
            if (column.ignore()) return true;
            if (column.isLogicDelete()) return true;
            boolean isSystemField = StrUtil.isNotEmpty(column.onInsertValue()) || StrUtil.isNotEmpty(column.onUpdateValue());
            if (isSystemField && !includeSystemFields) return true;
        }
        if (field.isAnnotationPresent(Id.class) && skipNullFields) return true;
        return false;
    }

    private void recordChange(String p, String n, Object o, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put("field", p); m.put("name", n); m.put("old", o); m.put("new", v);
        changes.add(m);
    }

    private Object extractId(Object obj) {
        try { return FieldUtil.getFieldValue(obj, "id"); } catch (Exception e) { return null; }
    }

    private boolean isSimpleType(Class<?> c) {
        return c.isPrimitive() || Number.class.isAssignableFrom(c) || c == String.class || c == Boolean.class
                || Date.class.isAssignableFrom(c) || c.isEnum() || c.getName().startsWith("java.time");
    }
}
