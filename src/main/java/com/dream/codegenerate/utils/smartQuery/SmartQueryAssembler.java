package com.dream.codegenerate.utils.smartQuery;

import cn.hutool.v7.core.collection.CollUtil;
import cn.hutool.v7.core.text.StrUtil;
import cn.hutool.v7.core.reflect.ConstructorUtil;
import com.dream.codegenerate.model.convert.SmartConvert;
import com.dream.codegenerate.utils.smartQuery.SmartQueryStructure.JoinNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryColumn;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 结果组装器：SmartQuery 的核心引擎
 * <p>
 * 集成特性：
 * 1. 极致性能：优先匹配 EntityFactory (零反射构建) 和 SmartConvert (零反射调用)。
 * 2. 贪婪匹配：优先使用参数最多的 Mapper 方法。
 * 3. 双重并行：支持横向(兄弟节点)和纵向(ID切分)的并行查询，最大化利用数据库 IO。
 * 4. 递归依赖：天然支持多级嵌套子查询 (A -> B -> C)，父级会自动等待子级数据就绪。
 * </p>
 */
@Slf4j
public class SmartQueryAssembler<R> {

    private final Class<R> resultClass;
    private final JoinNode rootNode;
    private final ObjectMapper objectMapper;

    // 注册表：TargetVOClass -> List<MapperMethod>
    private final Map<Class<?>, List<MapperEntry>> mapperRegistry = new ConcurrentHashMap<>();

    // 实体工厂注册表：EntityClass -> Factory
    private final Map<Class<?>, EntityFactory<?>> entityFactoryRegistry = new ConcurrentHashMap<>();

    // 函数式接口：手动从 Row 构建 Entity
    @FunctionalInterface
    public interface EntityFactory<E> {
        E create(Map<String, Object> row, String aliasPrefix);
    }

    private record MapperEntry(Object instance, Method method, Class<?>[] paramTypes, int paramCount) {}

    private static final Set<Class<?>> PRIMITIVE_TYPES = Set.of(
            String.class, Long.class, Integer.class, Double.class, Boolean.class,
            Date.class, LocalDate.class, LocalDateTime.class, BigDecimal.class
    );

    private record IdentityKey(Object parentVo, JoinNode node, Object pkValue) {}

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
            // 过滤掉 Object 方法、桥接方法以及 SmartConvert 接口自身的通用方法
            if (m.getParameterCount() > 0
                    && m.getReturnType() != void.class
                    && m.getDeclaringClass() != Object.class
                    && !m.isBridge()
                    && !(m.getParameterCount() == 1 && m.getParameterTypes()[0] == Object[].class)) {

                Class<?> targetType = m.getReturnType();
                mapperRegistry.computeIfAbsent(targetType, k -> new ArrayList<>())
                        .add(new MapperEntry(mapper, m, m.getParameterTypes(), m.getParameterCount()));
            }
        }
        // 【核心特性】贪婪匹配：对方法按参数数量降序排序
        // 确保优先匹配 toVO(App, User) [2参] 而不是 toVO(App) [1参]
        for (List<MapperEntry> entries : mapperRegistry.values()) {
            entries.sort((a, b) -> Integer.compare(b.paramCount, a.paramCount));
        }
    }

    public List<R> reconstruct(List<Map<String, Object>> rows) {
        Map<Object, R> rootMap = new LinkedHashMap<>(rows.size());
        Map<IdentityKey, Object> contextCache = new HashMap<>(rows.size() * 2);
        String rootPkKey = rootNode.voPkPropName != null ? rootNode.voPkPropName : "Flex_Internal_PK";

        for (Map<String, Object> row : rows) {
            Object pk = row.get(rootPkKey);
            if (pk == null) pk = row.hashCode();

            // 构建根对象
            R rootVo = rootMap.computeIfAbsent(pk, k -> buildNodeObject(row, "", rootNode));
            // 递归填充 Join 子对象
            fillRecursive(rootVo, row, "", rootNode, contextCache);
        }

        List<R> results = new ArrayList<>(rootMap.values());
        if (!results.isEmpty()) {
            // 处理延迟加载 (子查询)，此处为阻塞调用，处理完才会返回 results
            processDeferredTasks(results, rootNode);
        }
        return results;
    }

    @SuppressWarnings("unchecked")
    private <T> T buildNodeObject(Map<String, Object> row, String prefix, JoinNode node) {
        // 1. 优先尝试 Mapper
        List<MapperEntry> candidates = mapperRegistry.get(node.fieldType);
        if (candidates != null) {
            for (MapperEntry entry : candidates) {
                Object[] args = resolveArguments(entry.paramTypes, row, prefix, node);
                if (args != null) {
                    try {
                        // 【极致性能】如果实现了 SmartConvert 接口，直接强转调用，绕过反射
                        if (entry.instance instanceof SmartConvert smartConvert) {
                            T vo = (T) smartConvert.toVO(args);
                            if (vo != null) return vo;
                        } else {
                            // 降级：反射调用
                            T vo = (T) entry.method.invoke(entry.instance, args);
                            if (vo != null) return vo;
                        }
                    } catch (Exception e) {
                        log.warn("SmartQuery Mapper Error: {}", entry.method.getName(), e);
                    }
                }
            }
        }

        // 2. 降级：如果目标本来就是 Entity 类型 (直接走工厂)
        if (node.fieldType.isAssignableFrom(node.entityClass)) {
            return (T) buildEntity(row, prefix, node);
        }

        // 3. 兜底：反射创建 VO
        T vo = (T) ConstructorUtil.newInstance(node.fieldType);
        fillProperties(vo, row, prefix, node);
        return vo;
    }

    private Object buildEntity(Map<String, Object> row, String prefix, JoinNode node) {
        // 【极致性能】O(1) 查找工厂，直接 new 对象，零反射
        EntityFactory<?> factory = entityFactoryRegistry.get(node.entityClass);
        if (factory != null) {
            String aliasPrefix = prefix.isEmpty() ? "" : prefix + "$";
            return factory.create(row, aliasPrefix);
        } else {
            Object entity = ConstructorUtil.newInstance(node.entityClass);
            fillProperties(entity, row, prefix, node);
            return entity;
        }
    }

    private Object[] resolveArguments(Class<?>[] paramTypes, Map<String, Object> row, String currentPrefix, JoinNode currentNode) {
        Object[] args = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> neededType = paramTypes[i];

            // 参数是主实体
            if (neededType.isAssignableFrom(currentNode.entityClass)) {
                args[i] = buildEntity(row, currentPrefix, currentNode);
                continue;
            }

            // 参数是 Join 子实体
            JoinNode matchingChild = findChildNodeByEntity(currentNode, neededType);
            if (matchingChild != null) {
                String childPrefix = (currentPrefix.isEmpty() ? "" : currentPrefix + "$") + matchingChild.fieldName;
                // 【智能判空】如果关联表的主键为空 (Left Join 未匹配)，则传 null
                String pkAlias = childPrefix + "$" + (matchingChild.pkColName != null ? matchingChild.pkColName : "id");
                if (row.get(pkAlias) == null) {
                    args[i] = null;
                } else {
                    args[i] = buildEntity(row, childPrefix, matchingChild);
                }
                continue;
            }
            // 参数无法解析，跳过此 Mapper 方法
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
            setSafeValue(target, voPropName, row.get(alias));
        }
    }

    private void processDeferredTasks(List<?> parentObjects, JoinNode parentNode) {
        if (CollUtil.isEmpty(parentObjects)) return;

        // 【双重并行优化 1】横向并行 (Sibling Parallelism)
        // 如果一个 VO 有多个 @SmartFetch 列表 (如 tags, logs)，它们之间互不依赖
        // 使用 parallelStream 让它们同时发起查询，而不是串行等待
        if (CollUtil.isNotEmpty(parentNode.deferredChildren)) {
            parentNode.deferredChildren.parallelStream().forEach(deferNode -> {
                executeDeferredFetch(parentObjects, deferNode);
            });
        }

        // 递归检查 Join 进来的子对象，看它们是否还有深层嵌套的 deferred 任务
        // 这里的递归是串行的 (深度优先)，确保子节点完全加载完毕后，父节点才算完成
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

        final String finalRemoteCol = remoteCol;

        List<Object> knownMappers = mapperRegistry.values().stream()
                .flatMap(List::stream)
                .map(MapperEntry::instance)
                .distinct()
                .toList();

        // 【双重并行优化 2】纵向 ID 切分 (Batch Parallelism)
        // 1. 切分：默认 1000 为一批
        // 2. 并行：多批次 ID 同时查询
        final int BATCH_SIZE = 1000;
        List<Object> linkValueList = new ArrayList<>(linkValues);
        List<List<Object>> batches = new ArrayList<>();

        for (int i = 0; i < linkValueList.size(); i += BATCH_SIZE) {
            batches.add(linkValueList.subList(i, Math.min(i + BATCH_SIZE, linkValueList.size())));
        }

        List<Object> allChildren = batches.parallelStream()
                .map(batch -> (List<Object>) FlexSmartQuery.of(sf.targetEntity())
                        .bind(deferNode.fieldType) // 绑定子 VO
                        .withConverts(knownMappers.toArray()) // 透传 Mappers
                        .withFactories(this.entityFactoryRegistry) // 透传 Factories
                        .where(new QueryColumn("t0", finalRemoteCol).in(batch))
                        .list()) // 【递归核心】调用子查询，SmartQueryAssembler.reconstruct 会阻塞直到子数据加载完毕
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();

        // 内存组装 (Hash Match)
        Map<Object, List<Object>> groupedChildren = new HashMap<>();
        String remoteProp = SmartQueryContext.getPropertyByColumn(sf.targetEntity(), remoteCol);
        if (remoteProp == null) remoteProp = sf.remoteFieldLink();

        for (Object child : allChildren) {
            Object linkVal = getFieldValue(child, remoteProp);
            if (linkVal != null) groupedChildren.computeIfAbsent(linkVal, k -> new ArrayList<>()).add(child);
        }

        // 回填到父对象
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
