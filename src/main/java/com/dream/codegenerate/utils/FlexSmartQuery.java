package com.dream.codegenerate.utils;

import cn.hutool.v7.core.bean.BeanUtil;
import cn.hutool.v7.core.collection.CollUtil;
import cn.hutool.v7.core.text.StrUtil;
import cn.hutool.v7.core.reflect.ConstructorUtil;
import com.dream.codegenerate.mapper.UniversalSearchMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.mybatis.Mappers;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.*;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.mybatisflex.core.util.LambdaGetter;
import com.mybatisflex.core.util.LambdaUtil;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * ==================================================================================
 * 🚀 FlexSmartQuery - 智能查询一枪流引擎 (MyBatis-Flex 版)
 * ==================================================================================
 * 【主要功能】：
 * 1. 自动构建：根据 DTO 字段自动生成 MyBatis-Flex 的 QueryWrapper 条件。
 * 2. 自动回填：利用 @Relation 注解，实现跨表数据的“零代码”批量回填（Hydration）。
 * 3. 性能巅峰：采用 UNION ALL 机制，将原本 N+1 的查询优化为 1+1（主表查询+批量回填查询）。
 * 4. 泛型平滑：支持从 Entity 自动转换到 VO 并在转换过程中完成数据增强。
 * <p>
 * 【使用方法】：
 * <p>
 * 场景 A：基础 DTO 自动查询
 * Page<UserVO> result = FlexSmartQuery.of(User.class)
 * .autoBuild(userDTO) // 自动将 DTO 非空字段转为 QueryCondition (String->Like, List->In, Other->Eq)
 * .page(1, 10, UserVO.class);
 * <p>
 * 场景 B：进阶数据关联回填 (VO 增强)
 * // 1. 在 VO 中定义关联字段
 * public class UserVO {
 * private Long deptId;
 * @Relation(targetEntity = Dept.class, localField = "deptId", remoteField = "name")
 * private String deptName; // 自动根据 deptId 查出 Dept 表的 name 并填入
 * }
 * // 2. 调用 autoBind 开启引擎
 * List<UserVO> list = FlexSmartQuery.of(User.class)
 * .autoBind(UserVO.class) // 解析注解并准备回填任务
 * .autoBuild(dto)
 * .list();
 * <p>
 * 场景 C：手动 Lambda 绑定
 * FlexSmartQuery.of(User.class)
 * .bind(USER.DEPT_ID, Dept::getName, UserVO::setDeptName, MatchType.EQ)
 * .list(UserVO.class);
 * ==================================================================================
 */
public class FlexSmartQuery<E, R> {

    /**
     * 关联查询注解：标注在 VO 字段上，用于定义如何从其他表回填数据
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Relation {
        Class<?> targetEntity();   // 目标实体类（关联哪张表）
        String localField();       // 本地实体类中的关联属性名（如：deptId）
        String remoteField();      // 目标实体类中要提取的字段名（如：name）
        String remoteFieldLink() default ""; // 目标实体类中的连接字段，默认取主键
        MatchType matchType() default MatchType.CUSTOM; // 筛选类型（用于高级搜索）
    }

    // 静态依赖：由 SpringInitializer 在项目启动时注入
    private static UniversalSearchMapper searchMapper;
    private static ObjectMapper objectMapper;

    /**
     * Spring 自动初始化器：利用 Spring 的 @Component 机制将 Mapper 和 ObjectMapper 注入静态变量
     */
    @Component
    public static class SpringInitializer {
        @Autowired
        public SpringInitializer(UniversalSearchMapper mapper, ObjectMapper mapper2) {
            FlexSmartQuery.init(mapper, mapper2);
        }
    }

    public static void init(UniversalSearchMapper mapper, ObjectMapper mapper2) {
        searchMapper = mapper;
        objectMapper = mapper2;
    }

    // 用于判断字段是否需要 JSON 反序列化的简单类型集合
    private static final Set<Class<?>> SIMPLE_TYPES = Set.of(
            String.class, Long.class, Integer.class, Short.class, Byte.class, Double.class, Float.class,
            Boolean.class, Character.class, BigDecimal.class, Date.class, LocalDate.class, LocalDateTime.class
    );

    private final Class<E> entityClass;   // 主表实体类
    private final Class<R> resultClass;   // 返回结果类型 (可能是 Entity 或 VO)
    private final QueryWrapper queryWrapper; // MyBatis-Flex 查询包装器
    private final TableInfo tableInfo;    // 主表元数据信息
    private final Map<String, Rule> overrides; // 自定义查询规则覆盖（DTO 字段 -> 查询方式）
    private final Map<String, ExtractionTask> hydrationTasks; // 待执行的回填任务队列
    private final Set<QueryColumn> selectColumns; // 查询结果集中包含的列

    /**
     * 内部规则：用于存储字段如何匹配搜索值
     */
    private record Rule(QueryColumn column, MatchType type, BiFunction<QueryColumn, Object, QueryCondition> customFn) {}

    /**
     * 回填任务：描述一次跨表取值的逻辑
     */
    private static class ExtractionTask {
        String sourceKey;           // 任务唯一标识（用于 UNION ALL 后的结果归档）
        String tableName;           // 目标表名
        String linkCol;             // 目标表关联列
        String targetCol;           // 目标表取值列
        String localProp;           // 本地对象存储外键值的属性名
        BiConsumer<Object, Object> binder; // 设值器（将取到的值赋给对象）
        Class<?> targetType;        // 目标字段类型
        boolean isComplex;          // 是否为复杂对象（需要 Jackson 处理）
    }

    /**
     * 私有构造函数，开启查询流
     */
    private FlexSmartQuery(Class<E> entityClass, Class<R> resultClass) {
        this.entityClass = entityClass;
        this.resultClass = resultClass;
        this.tableInfo = TableInfoFactory.ofEntityClass(entityClass);
        this.queryWrapper = QueryWrapper.create().from(tableInfo.getTableName());
        this.overrides = new HashMap<>();
        this.hydrationTasks = new LinkedHashMap<>();
        this.selectColumns = new LinkedHashSet<>();
    }

    /**
     * 用于切换泛型（Entity -> VO）的拷贝构造函数
     */
    private <OldR> FlexSmartQuery(FlexSmartQuery<E, OldR> old, Class<R> newResultClass) {
        this.entityClass = old.entityClass;
        this.resultClass = newResultClass;
        this.tableInfo = old.tableInfo;
        this.queryWrapper = old.queryWrapper;
        this.overrides = old.overrides;
        this.hydrationTasks = old.hydrationTasks;
        this.selectColumns = old.selectColumns;
    }

    /**
     * 入口方法：创建一个针对某实体的智能查询流
     */
    public static <E> FlexSmartQuery<E, E> of(Class<E> entityClass) {
        return new FlexSmartQuery<>(entityClass, entityClass);
    }

    public enum MatchType { EQ, NE, GE, GT, LE, LT, LIKE, LEFT_LIKE, RIGHT_LIKE, IN, CUSTOM }

    /**
     * 核心方法：自动绑定 VO
     */
    public <V> FlexSmartQuery<E, V> autoBind(Class<V> voClass) {
        FlexSmartQuery<E, V> next = new FlexSmartQuery<>(this, voClass);
        next.select(voClass);

        var fields = voClass.getDeclaredFields();
        for (var field : fields) {
            if (field.isAnnotationPresent(Relation.class)) {
                Relation rel = field.getAnnotation(Relation.class);
                QueryColumn localCol = tableInfo.getQueryColumnByProperty(rel.localField());
                if (localCol == null) continue;

                String fieldName = field.getName();
                BiConsumer<Object, Object> voBinder = (Object target, Object value) ->
                        SystemMetaObject.forObject(target).setValue(fieldName, value);

                next.bind(localCol, rel.targetEntity(), rel.remoteField(), rel.remoteFieldLink(), voBinder, rel.matchType(), fieldName);
            }
        }
        return next;
    }

    /**
     * 根据类属性自动设置 Select 字段
     */
    public FlexSmartQuery<E, R> select(Class<?> clazz) {
        MetaObject meta = SystemMetaObject.forObject(ConstructorUtil.newInstance(clazz));
        for (String prop : meta.getGetterNames()) {
            QueryColumn col = tableInfo.getQueryColumnByProperty(prop);
            if (col != null) this.selectColumns.add(col);
        }
        return this;
    }

    private void applyProjection() {
        if (!this.selectColumns.isEmpty()) queryWrapper.select(this.selectColumns.toArray(new QueryColumn[0]));
    }

    // ========================================================================
    // 绑定体系 (手动绑定)
    // ========================================================================

    public <Target, T, V> FlexSmartQuery<E, R> bind(QueryColumn localColumn, LambdaGetter<Target> remoteField, BiConsumer<V, T> binder, MatchType filterType) {
        Class<Target> rClass = (Class<Target>) LambdaUtil.getImplClass(remoteField);
        String rProp = LambdaUtil.getFieldName(remoteField);
        return bind(localColumn, rClass, rProp, null, binder, filterType, rProp);
    }

    @SuppressWarnings("unchecked")
    public <V, T> FlexSmartQuery<E, R> bind(QueryColumn localColumn, Class<?> rClass, String rProp, String rLinkProp, BiConsumer<V, T> binder, MatchType filterType, String dtoPropName) {
        TableInfo rInfo = TableInfoFactory.ofEntityClass(rClass);
        // 使用抽取的全局缓存获取属性名
        String localFieldName = FlexTableMetaCache.getPropertyName(this.entityClass, localColumn.getName());

        String actualLinkCol = StrUtil.isNotBlank(rLinkProp) ?
                rInfo.getColumnByProperty(rLinkProp) :
                rInfo.getPrimaryKeyList().get(0).getColumn();

        if (filterType != null && filterType != MatchType.CUSTOM) {
            Rule searchRule = new Rule(null, MatchType.CUSTOM, (dummy, value) -> {
                QueryWrapper rQuery = QueryWrapper.create()
                        .select(actualLinkCol)
                        .from(rInfo.getTableName())
                        .where(buildCondition(new QueryColumn(rInfo.getTableName(), rInfo.getColumnByProperty(rProp)), value, filterType));
                List<Object> results = (List<Object>) Mappers.ofEntityClass(rClass).selectObjectListByQuery(rQuery);
                return CollUtil.isEmpty(results) ? makeFalseCondition(localColumn) : localColumn.in(results);
            });
            if (StrUtil.isNotBlank(dtoPropName)) overrides.put(dtoPropName, searchRule);
            overrides.put(localFieldName, searchRule);
        }

        if (binder != null) {
            ExtractionTask task = new ExtractionTask();
            task.sourceKey = UUID.randomUUID().toString().substring(0, 8);
            task.tableName = rInfo.getTableName();
            task.linkCol = actualLinkCol;
            task.targetCol = rInfo.getColumnByProperty(rProp);
            task.localProp = localFieldName;
            task.binder = (BiConsumer<Object, Object>) binder;
            try {
                var name = rProp.substring(0, 1).toUpperCase() + rProp.substring(1);
                Class<?> type;
                try {
                    type = rClass.getMethod("get" + name).getReturnType();
                } catch (NoSuchMethodException e) {
                    type = rClass.getMethod("is" + name).getReturnType();
                }
                task.targetType = type;
                task.isComplex = !SIMPLE_TYPES.contains(type) && !type.isPrimitive() && !type.isEnum();
            } catch (Exception e) {
                throw new RuntimeException("解析返回类型失败: " + rProp, e);
            }
            hydrationTasks.put(localFieldName + ":" + task.targetCol, task);
        }
        return this;
    }

    // ========================================================================
    // 终止执行引擎 (List / Page)
    // ========================================================================

    public List<R> list() {
        return list(this.resultClass);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> list(Class<T> targetClass) {
        if (targetClass != entityClass) this.select(targetClass);
        applyProjection();
        List<E> entities = Mappers.ofEntityClass(entityClass).selectListByQuery(queryWrapper);

        if (targetClass == entityClass) {
            executeHydration(entities);
            return (List<T>) entities;
        }

        List<T> targets = entities.stream().map(e -> BeanUtil.copyProperties(e, targetClass)).toList();
        executeHydration(targets);
        return targets;
    }

    public Page<R> page(int pageNum, int pageSize) {
        return page(pageNum, pageSize, this.resultClass);
    }

    @SuppressWarnings("unchecked")
    public <T> Page<T> page(int pageNum, int pageSize, Class<T> targetClass) {
        if (targetClass != entityClass) this.select(targetClass);
        applyProjection();
        Page<E> page = Mappers.ofEntityClass(entityClass).paginate(pageNum, pageSize, queryWrapper);

        if (targetClass == entityClass) {
            executeHydration(page.getRecords());
            return (Page<T>) page;
        }

        List<T> targets = page.getRecords().stream().map(e -> BeanUtil.copyProperties(e, targetClass)).toList();
        Page<T> targetPage = new Page<>(targets, pageNum, pageSize, page.getTotalRow());
        executeHydration(targetPage.getRecords());
        return targetPage;
    }

    private void executeHydration(List<?> targets) {
        if (CollUtil.isEmpty(targets) || hydrationTasks.isEmpty() || searchMapper == null) return;

        StringBuilder sql = new StringBuilder();
        var taskList = new ArrayList<>(hydrationTasks.values());

        for (var task : taskList) {
            Set<Object> ids = targets.stream()
                    .map(o -> SystemMetaObject.forObject(o).getValue(task.localProp))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (ids.isEmpty()) continue;
            if (sql.length() > 0) sql.append(" UNION ALL ");

            String idsStr = ids.stream()
                    .map(id -> id instanceof Number ? id.toString() : "'" + id.toString().replace("'", "''") + "'")
                    .collect(Collectors.joining(","));

            sql.append(String.format("SELECT '%s' as s, %s as k, %s as v FROM %s WHERE %s IN (%s)",
                    task.sourceKey, task.linkCol, task.targetCol, task.tableName, task.linkCol, idsStr));
        }

        if (sql.length() == 0) return;

        List<Map<String, Object>> dbResults = searchMapper.executeDynamicUnionQuery(sql.toString());
        Map<String, Map<String, String>> resultMap = new HashMap<>();
        for (var row : dbResults) {
            resultMap.computeIfAbsent(String.valueOf(row.get("s")), k -> new HashMap<>())
                    .put(String.valueOf(row.get("k")), String.valueOf(row.get("v")));
        }

        for (var target : targets) {
            MetaObject meta = SystemMetaObject.forObject(target);
            for (var task : taskList) {
                if (!meta.hasGetter(task.localProp)) continue;
                Object lid = meta.getValue(task.localProp);
                String raw = Optional.ofNullable(resultMap.get(task.sourceKey))
                        .map(m -> m.get(String.valueOf(lid)))
                        .orElse(null);

                if (raw != null) {
                    try {
                        Object val = task.isComplex ? objectMapper.readValue(raw, task.targetType) : BeanUtil.toBean(raw, task.targetType);
                        task.binder.accept(target, val);
                    } catch (Exception e) {
                        throw new RuntimeException("回填执行失败: " + task.localProp, e);
                    }
                }
            }
        }
    }

    public <D> FlexSmartQuery<E, R> autoBuild(D dto) {
        if (dto == null) return this;
        MetaObject metaDto = SystemMetaObject.forObject(dto);
        for (var fieldName : metaDto.getGetterNames()) {
            Object value = metaDto.getValue(fieldName);
            if (!isValid(value)) continue;

            Rule rule = overrides.get(fieldName);
            QueryColumn column = (rule != null && rule.column != null) ? rule.column : tableInfo.getQueryColumnByProperty(fieldName);
            MatchType type = (rule != null && rule.type != null) ? rule.type : determineDefaultType(value);

            if (type == MatchType.CUSTOM && rule != null && rule.customFn != null) {
                queryWrapper.and(rule.customFn.apply(column, value));
            } else if (column != null) {
                queryWrapper.and(buildCondition(column, value, type));
            }
        }
        return this;
    }

    private static QueryCondition buildCondition(QueryColumn column, Object value, MatchType type) {
        if (column == null) return null;
        return switch (type) {
            case EQ -> column.eq(value);
            case NE -> column.ne(value);
            case GE -> column.ge(value);
            case GT -> column.gt(value);
            case LE -> column.le(value);
            case LT -> column.lt(value);
            case LIKE -> column.like(value);
            case LEFT_LIKE -> column.likeLeft(value);
            case RIGHT_LIKE -> column.likeRight(value);
            case IN -> (value instanceof Collection<?> coll) ? column.in(coll) : column.eq(value);
            case CUSTOM -> null;
        };
    }

    private QueryCondition makeFalseCondition(QueryColumn fallbackColumn) {
        return QueryCondition.create(QueryMethods.raw("1").getColumn(), "=", 2);
    }

    private boolean isValid(Object value) {
        return switch (value) {
            case null -> false;
            case String s -> StrUtil.isNotBlank(s);
            case Collection<?> c -> CollUtil.isNotEmpty(c);
            default -> true;
        };
    }

    private MatchType determineDefaultType(Object value) {
        return switch (value) {
            case String s -> MatchType.LIKE;
            case Collection<?> c -> MatchType.IN;
            default -> MatchType.EQ;
        };
    }

    public FlexSmartQuery<E, R> orderBy(QueryOrderBy... orderBys) {
        if (orderBys != null) queryWrapper.orderBy(orderBys);
        return this;
    }
}
