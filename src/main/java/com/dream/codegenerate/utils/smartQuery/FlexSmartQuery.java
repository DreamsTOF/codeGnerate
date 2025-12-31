package com.dream.codegenerate.utils.smartQuery;

import cn.hutool.v7.core.collection.CollUtil;
import cn.hutool.v7.core.text.StrUtil;
import com.mybatisflex.core.mybatis.Mappers;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryMethods;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.util.LambdaGetter;
import com.mybatisflex.core.util.LambdaUtil;
import lombok.CustomLog;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiFunction;

import com.dream.codegenerate.utils.smartQuery.SmartQueryStructure.JoinNode;
import com.dream.codegenerate.utils.smartQuery.SmartQueryStructure.FilterMapping;
import com.dream.codegenerate.utils.smartQuery.SmartQueryContext.DtoFieldMeta;

/**
 * 🚀 FlexSmartQuery - The Ultimate Intelligent Query Engine
 */
@CustomLog
public class FlexSmartQuery<E, R> {

    @Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME) public @interface PageNo {}
    @Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME) public @interface PageSize {}
    @Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME) public @interface SortField {}
    @Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME) public @interface SortOrder {}

    private record OverrideRule(MatchType type, BiFunction<QueryColumn, Object, QueryCondition> customFn) {}
    private record AliasRule(Class<?> targetEntity, String targetProperty, String localKey, MatchType matchType) {}
    private record TargetColumn(String alias, QueryColumn column) {}

    private final Class<E> entityClass;
    private final Class<R> resultClass;
    private final QueryWrapper queryWrapper;
    private final SmartQueryStructure structure;

    private final List<Object> structMappers = new ArrayList<>();
    private final Map<Class<?>, SmartQueryAssembler.EntityFactory<?>> entityFactories = new HashMap<>();

    private final Map<String, OverrideRule> overrides = new HashMap<>();
    private final Map<String, AliasRule> manualAliasRules = new HashMap<>();
    private boolean useOrLogic = false;

    private FlexSmartQuery(Class<E> entityClass, Class<R> resultClass) {
        this.entityClass = entityClass;
        this.resultClass = resultClass;
        TableInfo rootTable = SmartQueryContext.getTableInfo(entityClass);
        this.queryWrapper = QueryWrapper.create().from(rootTable.getTableName()).as("t0");
        this.structure = new SmartQueryStructure(entityClass, resultClass);

        if (!SmartQueryConfig.FACTORIES.isEmpty()) {
            this.entityFactories.putAll(SmartQueryConfig.FACTORIES);
        }
    }

    public static <E> FlexSmartQuery<E, E> of(Class<E> entityClass) {
        return new FlexSmartQuery<>(entityClass, entityClass);
    }

    /* ==============================================================================
     * API
     * ============================================================================== */

    public <V> FlexSmartQuery<E, V> bind(Class<V> voClass) {
        FlexSmartQuery<E, V> next = new FlexSmartQuery<>(this.entityClass, voClass);
        next.structure.parseVoTree(voClass, next.structure.getRootNode(), 0);
        next.structure.applyToWrapper(next.queryWrapper, next.structure.getRootNode());

        next.structMappers.addAll(this.structMappers);
        next.entityFactories.putAll(this.entityFactories);

        return next;
    }

    /**
     * 注册 Mapper 实例
     * 推荐传递单例，如：withMapper(AppConvert.INSTANCE)
     */
    public FlexSmartQuery<E, R> withConverts(Object... mappers) {
        if (mappers != null) {
            Collections.addAll(this.structMappers, mappers);
        }
        return this;
    }

    public FlexSmartQuery<E, R> withConvert(Object mapper) {
        return withConverts(mapper);
    }

    public <T> FlexSmartQuery<E, R> registerFactory(Class<T> entityClass, SmartQueryAssembler.EntityFactory<T> factory) {
        this.entityFactories.put(entityClass, factory);
        return this;
    }

    public FlexSmartQuery<E, R> withFactories(Map<Class<?>, SmartQueryAssembler.EntityFactory<?>> factories) {
        if (factories != null) {
            this.entityFactories.putAll(factories);
        }
        return this;
    }

    public <D, T> FlexSmartQuery<E, R> map(LambdaGetter<D> dtoGetter, Class<T> targetEntity, LambdaGetter<T> targetGetter, LambdaGetter<E> localKeyGetter) {
        String dtoField = LambdaUtil.getFieldName(dtoGetter);
        String targetProp = LambdaUtil.getFieldName(targetGetter);
        String localKey = localKeyGetter != null ? LambdaUtil.getFieldName(localKeyGetter) : "";
        this.manualAliasRules.put(dtoField, new AliasRule(targetEntity, targetProp, localKey, MatchType.CUSTOM));
        return this;
    }

    public <D, T> FlexSmartQuery<E, R> map(LambdaGetter<D> dtoGetter, Class<T> targetEntity, LambdaGetter<T> targetGetter) {
        return map(dtoGetter, targetEntity, targetGetter, null);
    }

    public <D> FlexSmartQuery<E, R> override(LambdaGetter<D> getter, MatchType type) {
        this.overrides.put(LambdaUtil.getFieldName(getter), new OverrideRule(type, null));
        return this;
    }

    public <D> FlexSmartQuery<E, R> override(LambdaGetter<D> getter, BiFunction<QueryColumn, Object, QueryCondition> customFn) {
        this.overrides.put(LambdaUtil.getFieldName(getter), new OverrideRule(MatchType.CUSTOM, customFn));
        return this;
    }

    public <D> FlexSmartQuery<E, R> autoBuild(D queryDto) {
        if (queryDto == null) return this;
        MetaObject meta = SystemMetaObject.forObject(queryDto);
        Map<String, DtoFieldMeta> dtoMetas = SmartQueryContext.getDtoFields(queryDto.getClass());

        for (String field : meta.getGetterNames()) {
            Object val = meta.getValue(field);
            if (!isSafeForQuery(val)) continue;

            if (overrides.containsKey(field)) {
                OverrideRule rule = overrides.get(field);
                if (rule.customFn != null) {
                    TargetColumn target = findColumnRecursively(field);
                    QueryColumn col = target != null ? target.column : new QueryColumn("t0", field);
                    addCondition(rule.customFn.apply(col, val));
                    continue;
                }
            }

            AliasRule aliasRule = manualAliasRules.get(field);
            if (aliasRule == null && dtoMetas.containsKey(field) && dtoMetas.get(field).mapping() != null) {
                QueryMapping m = dtoMetas.get(field).mapping();
                aliasRule = new AliasRule(m.targetEntity(), m.targetProperty(), m.localKey(), m.matchType());
            }

            if (aliasRule != null) {
                applyExplicitMapping(aliasRule, field, val, dtoMetas.get(field));
                continue;
            }

            if (structure.filterRegistry.containsKey(field)) {
                FilterMapping mapping = structure.filterRegistry.get(field);
                MatchType type = resolveMatchType(field, null, val, mapping.type());
                addCondition(mapping.column(), val, type);
                continue;
            }

            QueryColumn rootCol = SmartQueryContext.getTableInfo(entityClass).getQueryColumnByProperty(field);
            if (rootCol != null) {
                MatchType type = resolveMatchType(field, dtoMetas.get(field), val, MatchType.CUSTOM);
                addCondition(new QueryColumn("t0", rootCol.getName()), val, type);
            }
        }

        handleProtocol(queryDto);
        return this;
    }

    public FlexSmartQuery<E, R> where(QueryCondition condition) {
        addCondition(condition);
        return this;
    }

    public FlexSmartQuery<E, R> log() { log.info("\nSQL: {}", queryWrapper.toSQL()); return this; }
    public FlexSmartQuery<E, R> useOr() { this.useOrLogic = true; return this; }

    /* ==============================================================================
     * Execution
     * ============================================================================== */

    public List<R> list() {
        List<Map<String, Object>> rows = SmartQueryContext.getMapper().executeDynamicUnionQuery(queryWrapper.toSQL());
        return rows.isEmpty() ? Collections.emptyList() : new SmartQueryAssembler<>(resultClass, structure.getRootNode(), structMappers, entityFactories).reconstruct(rows);
    }

    public Page<R> page(Object pageObj) {
        long current = extractProtocol(pageObj, PageNo.class, Number.class).map(Number::longValue).orElse(1L);
        long size = extractProtocol(pageObj, PageSize.class, Number.class).map(Number::longValue).orElse(10L);
        return page(current, size);
    }

    public Page<R> page(long num, long size) {
        Page<Row> rowPage = Db.paginate(null, Page.of(num, size), queryWrapper);
        List<Map<String, Object>> records = new ArrayList<>(rowPage.getRecords());
        List<R> results = new SmartQueryAssembler<>(resultClass, structure.getRootNode(), structMappers, entityFactories).reconstruct(records);
        return new Page<>(results, rowPage.getPageNumber(), rowPage.getPageSize(), rowPage.getTotalRow());
    }

    public List<R> seek(Object lastId, long size) {
        QueryColumn pkCol = new QueryColumn("t0", structure.getRootNode().pkColName);
        queryWrapper.orderBy(pkCol.asc());
        if (lastId != null) queryWrapper.where(pkCol.gt(lastId));
        queryWrapper.limit(size);
        List<Map<String, Object>> rows = SmartQueryContext.getMapper().executeDynamicUnionQuery(queryWrapper.toSQL());
        return rows.isEmpty() ? Collections.emptyList() : new SmartQueryAssembler<>(resultClass, structure.getRootNode(), structMappers, entityFactories).reconstruct(rows);
    }

    public List<R> seek(long lastId, long size) { return seek((Object) lastId, size); }
    public List<R> seek(String lastId, long size) { return seek((Object) lastId, size); }

    /* ==============================================================================
     * Internal Helpers
     * ============================================================================== */
    private void applyExplicitMapping(AliasRule rule, String fieldName, Object val, DtoFieldMeta meta) {
        TargetColumn target = findColumnInJoinTreeByEntity(structure.getRootNode(), rule.targetEntity, rule.targetProperty);
        MatchType type = resolveMatchType(fieldName, meta, val, rule.matchType);

        if (target != null) {
            addCondition(new QueryColumn(target.alias, target.column.getName()), val, type);
        } else if (StrUtil.isNotBlank(rule.localKey)) {
            addCrossDbCondition(rule, val, type);
        } else {
            log.warn("⚠️ [FlexSmartQuery] 映射无效: Join树无 [{}] 且 localKey 为空", rule.targetEntity.getSimpleName());
        }
    }

    private void addCrossDbCondition(AliasRule rule, Object val, MatchType type) {
        TableInfo rInfo = SmartQueryContext.getTableInfo(rule.targetEntity);
        if (rInfo == null) return;
        QueryWrapper subQuery = QueryWrapper.create()
                .select(rInfo.getPrimaryKeyList().getFirst().getColumn()).from(rInfo.getTableName())
                .where(buildBaseCondition(new QueryColumn(rInfo.getTableName(), rInfo.getColumnByProperty(rule.targetProperty)), val, type));
        List<Object> ids = Mappers.ofEntityClass(rule.targetEntity).selectObjectListByQuery(subQuery);

        QueryColumn localCol = SmartQueryContext.getTableInfo(entityClass).getQueryColumnByProperty(rule.localKey);
        if (localCol != null) {
            if (CollUtil.isEmpty(ids)) addCondition(QueryCondition.create(QueryMethods.raw("1").getColumn(), "=", 2));
            else addCondition(new QueryColumn("t0", localCol.getName()).in(ids));
        }
    }

    private TargetColumn findColumnInJoinTreeByEntity(JoinNode node, Class<?> targetEntity, String propertyName) {
        if (node.entityClass != null && node.entityClass.equals(targetEntity)) {
            QueryColumn col = node.tableInfo.getQueryColumnByProperty(propertyName);
            if (col != null) return new TargetColumn(node.tableAlias, col);
        }
        for (JoinNode child : node.children.values()) {
            TargetColumn found = findColumnInJoinTreeByEntity(child, targetEntity, propertyName);
            if (found != null) return found;
        }
        return null;
    }

    private TargetColumn findColumnRecursively(String propertyName) {
        QueryColumn rootCol = SmartQueryContext.getTableInfo(entityClass).getQueryColumnByProperty(propertyName);
        if (rootCol != null) return new TargetColumn("t0", rootCol);
        return null;
    }

    private MatchType resolveMatchType(String fieldName, DtoFieldMeta meta, Object value, MatchType preferred) {
        if (overrides.containsKey(fieldName)) return overrides.get(fieldName).type;
        if (preferred != MatchType.CUSTOM) return preferred;

        if (fieldName.endsWith("Like")) return MatchType.LIKE;
        if (fieldName.endsWith("Begin") || fieldName.endsWith("Start")) return MatchType.GE;
        if (fieldName.endsWith("End") || fieldName.endsWith("Finish")) return MatchType.LE;
        if (fieldName.endsWith("List") || fieldName.endsWith("Ids")) return MatchType.IN;

        Class<?> type = meta != null ? meta.type() : (value != null ? value.getClass() : null);
        if (type != null) {
            if (Collection.class.isAssignableFrom(type)) return MatchType.IN;
            if (String.class.isAssignableFrom(type)) return MatchType.LIKE;
        }
        return MatchType.EQ;
    }

    private void addCondition(QueryCondition cond) {
        if (cond != null) { if (useOrLogic) queryWrapper.or(cond); else queryWrapper.and(cond); }
    }

    private void addCondition(QueryColumn col, Object val, MatchType type) {
        addCondition(buildBaseCondition(col, val, type));
    }

    private QueryCondition buildBaseCondition(QueryColumn col, Object val, MatchType type) {
        Object finalVal = val;
        if ((type == MatchType.LIKE || type == MatchType.LEFT_LIKE || type == MatchType.RIGHT_LIKE) && val instanceof String s) {
            if (StrUtil.isBlank(s)) return null;
            finalVal = s.trim();
        }
        return switch (type) {
            case EQ -> col.eq(finalVal);
            case NE -> col.ne(finalVal);
            case LIKE -> col.like(finalVal);
            case LEFT_LIKE -> col.likeLeft(finalVal);
            case RIGHT_LIKE -> col.likeRight(finalVal);
            case GE -> col.ge(finalVal);
            case GT -> col.gt(finalVal);
            case LE -> col.le(finalVal);
            case LT -> col.lt(finalVal);
            case IN -> (finalVal instanceof Collection<?> c) ? col.in(c) : col.eq(finalVal);
            case NOT_IN -> (finalVal instanceof Collection<?> c) ? col.notIn(c) : col.ne(finalVal);
            default -> null;
        };
    }

    private void handleProtocol(Object dto) {
        extractProtocol(dto, SortField.class, String.class).ifPresent(f -> {
            String o = extractProtocol(dto, SortOrder.class, String.class).orElse("asc");
            TargetColumn target = findColumnRecursively(f);
            if (target != null) {
                QueryColumn col = new QueryColumn(target.alias, target.column.getName());
                if (o.toLowerCase().contains("desc")) queryWrapper.orderBy(col.desc());
                else queryWrapper.orderBy(col.asc());
            }
        });
    }

    private <T> Optional<T> extractProtocol(Object o, Class<? extends Annotation> a, Class<T> t) {
        for (Field f : o.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(a)) {
                try {
                    Object v = SystemMetaObject.forObject(o).getValue(f.getName());
                    if (v != null && t.isAssignableFrom(v.getClass())) return Optional.of((T) v);
                } catch (Exception ignored) {}
            }
        }
        return Optional.empty();
    }

    private boolean isSafeForQuery(Object v) {
        return v != null && (!(v instanceof String s) || StrUtil.isNotBlank(s)) && (!(v instanceof Collection<?> c) || !c.isEmpty());
    }
}
