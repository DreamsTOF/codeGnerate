package com.dream.codegenerate.utils.smartQuery;

import cn.hutool.v7.core.collection.CollUtil;
import cn.hutool.v7.core.text.StrUtil;
import cn.hutool.v7.core.reflect.ConstructorUtil;
import com.dream.codegenerate.utils.smartQuery.SmartQueryStructure.JoinNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryColumn;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 结果组装器：处理 JDBC 结果集映射与延迟加载
 */
public class SmartQueryAssembler<R> {

    private final Class<R> resultClass;
    private final JoinNode rootNode;
    private final ObjectMapper objectMapper;
    private static final Set<Class<?>> PRIMITIVE_TYPES = Set.of(
            String.class, Long.class, Integer.class, Double.class, Boolean.class,
            Date.class, LocalDate.class, LocalDateTime.class, BigDecimal.class
    );

    private record IdentityKey(Object parentVo, JoinNode node, Object pkValue) {}

    public SmartQueryAssembler(Class<R> resultClass, JoinNode rootNode) {
        this.resultClass = resultClass;
        this.rootNode = rootNode;
        this.objectMapper = SmartQueryContext.getObjectMapper();
    }

    public List<R> reconstruct(List<Map<String, Object>> rows) {
        Map<Object, R> rootMap = new LinkedHashMap<>();
        Map<IdentityKey, Object> contextCache = new HashMap<>();
        String rootPkKey = rootNode.voPkPropName != null ? rootNode.voPkPropName : "Flex_Internal_PK";

        for (Map<String, Object> row : rows) {
            Object pk = row.get(rootPkKey);
            if (pk == null) pk = row.hashCode();
            R rootVo = rootMap.computeIfAbsent(pk, k -> buildVo(row, "", resultClass, rootNode));
            fillRecursive(rootVo, row, "", rootNode, contextCache);
        }

        List<R> results = new ArrayList<>(rootMap.values());
        if (!results.isEmpty()) {
            processDeferredTasks(results, rootNode);
        }
        return results;
    }

    private void fillRecursive(Object currentVo, Map<String, Object> row, String path, JoinNode currentNode, Map<IdentityKey, Object> context) {
        for (var entry : currentNode.children.entrySet()) {
            String fieldName = entry.getKey();
            JoinNode childNode = entry.getValue();
            String aliasPrefix = (path.isEmpty() ? "" : path + "$") + fieldName;

            if (childNode.isLeaf) {
                // 处理 @Relation 单列
                Object val = row.get(aliasPrefix + "$" + fieldName);
                if (val != null) {
                    setSafeValue(currentVo, fieldName, convertValue(val, childNode.fieldType));
                }
                continue;
            }

            // 处理 @SmartFetch 嵌套对象
            String pkAliasKey = childNode.voPkPropName != null ? childNode.voPkPropName : "Flex_Internal_PK";
            Object childDbId = row.get(aliasPrefix + "$" + pkAliasKey);
            if (childDbId == null) continue;

            MetaObject meta = SystemMetaObject.forObject(currentVo);
            if (childNode.isCollection) {
                List<Object> list = (List<Object>) meta.getValue(fieldName);
                if (list == null) { list = new ArrayList<>(); meta.setValue(fieldName, list); }

                IdentityKey cacheKey = new IdentityKey(currentVo, childNode, childDbId);
                Object childVo = context.get(cacheKey);
                if (childVo == null) {
                    childVo = buildVo(row, aliasPrefix, childNode.fieldType, childNode);
                    list.add(childVo);
                    context.put(cacheKey, childVo);
                }
                fillRecursive(childVo, row, aliasPrefix, childNode, context);
            } else {
                Object childVo = meta.getValue(fieldName);
                if (childVo == null) {
                    childVo = buildVo(row, aliasPrefix, childNode.fieldType, childNode);
                    meta.setValue(fieldName, childVo);
                }
                fillRecursive(childVo, row, aliasPrefix, childNode, context);
            }
        }
    }

    private void processDeferredTasks(List<?> parentObjects, JoinNode parentNode) {
        if (CollUtil.isEmpty(parentObjects)) return;

        // 1. 执行当前节点的延迟子查询
        for (JoinNode deferNode : parentNode.deferredChildren) {
            executeDeferredFetch(parentObjects, deferNode);
        }

        // 2. 递归处理已 Join 的子节点
        for (JoinNode childNode : parentNode.children.values()) {
            if (childNode.isLeaf) continue;
            List<Object> children = new ArrayList<>();
            for (Object parent : parentObjects) {
                Object childVal = getFieldValue(parent, childNode.fieldName);
                if (childVal == null) continue;
                if (childVal instanceof Collection<?> c) children.addAll(c);
                else children.add(childVal);
            }
            if (!children.isEmpty()) processDeferredTasks(children, childNode);
        }
    }

    private void executeDeferredFetch(List<?> parents, JoinNode deferNode) {
        SmartFetch sf = deferNode.originalFetch;
        Set<Object> linkValues = new HashSet<>();
        for (Object parent : parents) {
            Object val = getFieldValue(parent, sf.localField());
            if (val != null) linkValues.add(val);
        }
        if (linkValues.isEmpty()) return;

        String remoteCol = StrUtil.isNotBlank(sf.remoteFieldLink()) ?
                SmartQueryContext.getTableInfo(sf.targetEntity()).getColumnByProperty(sf.remoteFieldLink()) :
                SmartQueryContext.getTableInfo(sf.targetEntity()).getPrimaryKeyList().getFirst().getColumn();

        // 递归调用主入口 FlexSmartQuery 进行查询 (避免代码重复)
        List<?> children = FlexSmartQuery.of(sf.targetEntity())
                .bind(deferNode.fieldType)
                .where(new QueryColumn("t0", remoteCol).in(linkValues))
                .list();

        // 内存组装
        Map<Object, List<Object>> groupedChildren = new HashMap<>();

        // 【修正】使用缓存反查属性名
        String remoteProp = SmartQueryContext.getPropertyByColumn(sf.targetEntity(), remoteCol);
        if (remoteProp == null) remoteProp = sf.remoteFieldLink();

        for (Object child : children) {
            Object linkVal = getFieldValue(child, remoteProp);
            if (linkVal != null) groupedChildren.computeIfAbsent(linkVal, k -> new ArrayList<>()).add(child);
        }

        for (Object parent : parents) {
            Object linkVal = getFieldValue(parent, sf.localField());
            List<Object> matches = groupedChildren.get(linkVal);
            if (matches != null) {
                MetaObject meta = SystemMetaObject.forObject(parent);
                if (deferNode.isCollection) meta.setValue(deferNode.fieldName, matches);
                else if (!matches.isEmpty()) meta.setValue(deferNode.fieldName, matches.get(0));
            }
        }
    }

    private <T> T buildVo(Map<String, Object> row, String prefix, Class<T> clazz, JoinNode node) {
        T vo = ConstructorUtil.newInstance(clazz);
        MetaObject meta = SystemMetaObject.forObject(vo);
        String aliasPrefix = prefix.isEmpty() ? "" : prefix + "$";
        for (String fieldName : node.selectFields.keySet()) {
            if ("Flex_Internal_PK".equals(fieldName)) continue;
            String alias = aliasPrefix + fieldName;
            if (row.containsKey(alias)) {
                setSafeValue(vo, fieldName, row.get(alias));
            }
        }
        return vo;
    }

    private void setSafeValue(Object target, String field, Object value) {
        try { SystemMetaObject.forObject(target).setValue(field, value); } catch (Exception ignored) {}
    }

    private Object getFieldValue(Object obj, String propName) {
        try { return SystemMetaObject.forObject(obj).getValue(propName); } catch (Exception e) { return null; }
    }

    private Object convertValue(Object val, Class<?> targetType) {
        if (val == null) return null;
        if (PRIMITIVE_TYPES.contains(targetType) || targetType.isInstance(val)) return val;
        try { return objectMapper.readValue(val.toString(), targetType); } catch (Exception e) { return val; }
    }
}
