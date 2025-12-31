package com.dream.codegenerate.utils.smartQuery;

import cn.hutool.v7.core.collection.CollUtil;
import cn.hutool.v7.core.text.StrUtil;
import cn.hutool.v7.core.reflect.ConstructorUtil;
import com.dream.codegenerate.utils.smartQuery.SmartQueryStructure.JoinNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryColumn;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 结果组装器：处理 JDBC 结果集映射与延迟加载
 * <p>
 * 极致性能版：
 * 1. 优先匹配手动注册的 EntityFactory (零反射，性能起飞)。
 * 2. 其次匹配 MapStruct (Target-Oriented，类型安全)。
 * 3. 最后兜底反射 (开发便利)。
 * </p>
 */
public class SmartQueryAssembler<R> {

    private final Class<R> resultClass;
    private final JoinNode rootNode;
    private final ObjectMapper objectMapper;

    // 注册表：TargetVOClass -> List<MapperMethod>
    private final Map<Class<?>, List<MapperEntry>> mapperRegistry = new ConcurrentHashMap<>();

    // 【核心】实体工厂注册表：EntityClass -> Factory
    private final Map<Class<?>, EntityFactory<?>> entityFactoryRegistry = new ConcurrentHashMap<>();

    // 函数式接口：手动从 Row 构建 Entity (由 AI 生成实现，性能极高)
    @FunctionalInterface
    public interface EntityFactory<E> {
        /**
         * @param row 数据库行数据 (Map)
         * @param aliasPrefix 当前节点别名前缀 (如 "items$")
         * @return 填充好的 Entity
         */
        E create(Map<String, Object> row, String aliasPrefix);
    }

    private record MapperEntry(Object instance, Method method, Class<?>[] paramTypes) {}

    private static final Set<Class<?>> PRIMITIVE_TYPES = Set.of(
            String.class, Long.class, Integer.class, Double.class, Boolean.class,
            Date.class, LocalDate.class, LocalDateTime.class, BigDecimal.class
    );

    private record IdentityKey(Object parentVo, JoinNode node, Object pkValue) {}

    public SmartQueryAssembler(Class<R> resultClass, JoinNode rootNode) {
        this(resultClass, rootNode, Collections.emptyList(), Collections.emptyMap());
    }

    public SmartQueryAssembler(Class<R> resultClass, JoinNode rootNode, List<Object> mappers) {
        this(resultClass, rootNode, mappers, Collections.emptyMap());
    }

    // 【全参构造】接收 Mapper 和 Factory
    public SmartQueryAssembler(Class<R> resultClass, JoinNode rootNode, List<Object> mappers, Map<Class<?>, EntityFactory<?>> factories) {
        this.resultClass = resultClass;
        this.rootNode = rootNode;
        this.objectMapper = SmartQueryContext.getObjectMapper();

        if (CollUtil.isNotEmpty(mappers)) {
            mappers.forEach(this::registerMapper);
        }
        if (CollUtil.isNotEmpty(factories)) {
            this.entityFactoryRegistry.putAll(factories);
        }
    }

    private void registerMapper(Object mapper) {
        if (mapper == null) return;
        for (Method m : mapper.getClass().getMethods()) {
            if (m.getParameterCount() > 0
                    && m.getReturnType() != void.class
                    && m.getDeclaringClass() != Object.class) {

                Class<?> targetType = m.getReturnType();
                mapperRegistry.computeIfAbsent(targetType, k -> new ArrayList<>())
                        .add(new MapperEntry(mapper, m, m.getParameterTypes()));
            }
        }
    }

    public List<R> reconstruct(List<Map<String, Object>> rows) {
        // 在高并发下，适当设置 InitialCapacity 可以减少 Resize 开销
        Map<Object, R> rootMap = new LinkedHashMap<>(rows.size());
        Map<IdentityKey, Object> contextCache = new HashMap<>(rows.size() * 2);
        String rootPkKey = rootNode.voPkPropName != null ? rootNode.voPkPropName : "Flex_Internal_PK";

        for (Map<String, Object> row : rows) {
            Object pk = row.get(rootPkKey);
            if (pk == null) pk = row.hashCode();

            R rootVo = rootMap.computeIfAbsent(pk, k -> buildNodeObject(row, "", rootNode));
            fillRecursive(rootVo, row, "", rootNode, contextCache);
        }

        List<R> results = new ArrayList<>(rootMap.values());
        if (!results.isEmpty()) {
            processDeferredTasks(results, rootNode);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private <T> T buildNodeObject(Map<String, Object> row, String prefix, JoinNode node) {
        // 1. 优先尝试 Mapper (MapStruct)
        List<MapperEntry> candidates = mapperRegistry.get(node.fieldType);
        if (candidates != null) {
            for (MapperEntry entry : candidates) {
                Object[] args = resolveArguments(entry.paramTypes, row, prefix, node);
                if (args != null) {
                    try {
                        T vo = (T) entry.method.invoke(entry.instance, args);
                        if (vo != null) return vo;
                    } catch (Exception ignored) { }
                }
            }
        }

        // 2. 降级：如果目标本来就是 Entity 类型 (如 List<Entity>)
        // 这里会优先调用 Factory 构建 Entity
        if (node.fieldType.isAssignableFrom(node.entityClass)) {
            return (T) buildEntity(row, prefix, node);
        }

        // 3. 兜底：反射创建 VO
        T vo = (T) ConstructorUtil.newInstance(node.fieldType);
        fillProperties(vo, row, prefix, node);
        return vo;
    }

    /**
     * 【性能关键点】构建 Entity
     */
    @SuppressWarnings("unchecked")
    private Object buildEntity(Map<String, Object> row, String prefix, JoinNode node) {
        // ⚡️ O(1) 查找工厂
        EntityFactory<?> factory = entityFactoryRegistry.get(node.entityClass);
        if (factory != null) {
            String aliasPrefix = prefix.isEmpty() ? "" : prefix + "$";
            // ⚡️ 直接调用，无反射
            return factory.create(row, aliasPrefix);
        } else {
            // 🐢 反射兜底
            Object entity = ConstructorUtil.newInstance(node.entityClass);
            fillProperties(entity, row, prefix, node);
            return entity;
        }
    }

    private Object[] resolveArguments(Class<?>[] paramTypes, Map<String, Object> row, String currentPrefix, JoinNode currentNode) {
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> neededType = paramTypes[i];

            if (neededType.isAssignableFrom(currentNode.entityClass)) {
                args[i] = buildEntity(row, currentPrefix, currentNode);
                continue;
            }

            JoinNode matchingChild = findChildNodeByEntity(currentNode, neededType);
            if (matchingChild != null) {
                String childPrefix = (currentPrefix.isEmpty() ? "" : currentPrefix + "$") + matchingChild.fieldName;
                args[i] = buildEntity(row, childPrefix, matchingChild);
                continue;
            }
            return null;
        }
        return args;
    }

    private JoinNode findChildNodeByEntity(JoinNode parent, Class<?> targetEntity) {
        for (Map.Entry<String, JoinNode> entry : parent.children.entrySet()) {
            JoinNode child = entry.getValue();
            if (targetEntity.isAssignableFrom(child.entityClass)) {
                return child;
            }
        }
        return null;
    }

    private void fillRecursive(Object currentVo, Map<String, Object> row, String path, JoinNode currentNode, Map<IdentityKey, Object> context) {
        for (var entry : currentNode.children.entrySet()) {
            String fieldName = entry.getKey();
            JoinNode childNode = entry.getValue();
            String aliasPrefix = (path.isEmpty() ? "" : path + "$") + fieldName;

            if (childNode.isLeaf) {
                Object val = row.get(aliasPrefix + "$" + fieldName);
                if (val != null) setSafeValue(currentVo, fieldName, convertValue(val, childNode.fieldType));
                continue;
            }

            String pkAliasKey = childNode.voPkPropName != null ? childNode.voPkPropName : "Flex_Internal_PK";
            Object childDbId = row.get(aliasPrefix + "$" + pkAliasKey);
            if (childDbId == null) continue;

            MetaObject meta = SystemMetaObject.forObject(currentVo);
            if (childNode.isCollection) {
                List<Object> list = (List<Object>) meta.getValue(fieldName);
                if (list == null) { list = new ArrayList<>(); meta.setValue(fieldName, list); }

                IdentityKey cacheKey = new IdentityKey(currentVo, childNode, childDbId);
                Object childObj = context.get(cacheKey);
                if (childObj == null) {
                    childObj = buildNodeObject(row, aliasPrefix, childNode);
                    list.add(childObj);
                    context.put(cacheKey, childObj);
                }
                fillRecursive(childObj, row, aliasPrefix, childNode, context);
            } else {
                Object childVo = meta.getValue(fieldName);
                if (childVo == null) {
                    childVo = buildNodeObject(row, aliasPrefix, childNode);
                    meta.setValue(fieldName, childVo);
                }
                fillRecursive(childVo, row, aliasPrefix, childNode, context);
            }
        }
    }

    private void fillProperties(Object target, Map<String, Object> row, String prefix, JoinNode node) {
        String aliasPrefix = prefix.isEmpty() ? "" : prefix + "$";
        for (Map.Entry<String, String> entry : node.selectFields.entrySet()) {
            String voPropName = entry.getKey();
            String alias = aliasPrefix + voPropName;
            if (!row.containsKey(alias)) continue;
            Object val = row.get(alias);

            String targetPropName = voPropName;
            if (target.getClass() == node.entityClass) {
                String p = SmartQueryContext.getPropertyByColumn(node.entityClass, entry.getValue());
                if (p != null) targetPropName = p;
            }
            setSafeValue(target, targetPropName, val);
        }
    }

    private void processDeferredTasks(List<?> parentObjects, JoinNode parentNode) {
        if (CollUtil.isEmpty(parentObjects)) return;
        for (JoinNode deferNode : parentNode.deferredChildren) {
            executeDeferredFetch(parentObjects, deferNode);
        }
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

        List<Object> knownMappers = mapperRegistry.values().stream()
                .flatMap(List::stream)
                .map(MapperEntry::instance)
                .distinct()
                .toList();

        // 传递当前环境的所有 Factories 到子查询
        List<?> children = FlexSmartQuery.of(sf.targetEntity())
                .bind(deferNode.fieldType)
                .withMappers(knownMappers.toArray())
                .withFactories(this.entityFactoryRegistry) // 【核心透传】
                .where(new QueryColumn("t0", remoteCol).in(linkValues))
                .list();

        Map<Object, List<Object>> groupedChildren = new HashMap<>();
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
