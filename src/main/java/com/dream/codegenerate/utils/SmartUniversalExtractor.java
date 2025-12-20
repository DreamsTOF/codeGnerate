package com.dream.codegenerate.utils;

import com.dream.codegenerate.mapper.UniversalSearchMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.mybatisflex.core.util.LambdaGetter;
import com.mybatisflex.core.util.LambdaUtil;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 智能一枪流萃取器 (MyBatis-Flex 适配版)
 * <p>
 * 核心功能：
 * 解决列表查询后的 "N+1" 关联数据填充问题。
 * 它不使用传统的 SQL Join（会导致主查询变慢），也不使用循环查库（性能极差）。
 * 而是收集所有需要的关联 ID，拼装成一个巨大的 UNION ALL SQL，一次性回填所有关联数据。
 * <p>
 * 特性：
 * 1. 泛型 ID 支持：支持 Long, Integer, String (UUID) 等主键类型。
 * 2. 自动 JSON 解析：如果目标字段是复杂对象，会自动尝试反序列化。
 * 3. 类型安全：基于 MyBatis-Flex 的 LambdaGetter，避免手写列名字符串。
 */
public class SmartUniversalExtractor<E, V> {

    // 全局 Jackson 对象，用于处理 JSON 类型的反序列化
    private static ObjectMapper OBJECT_MAPPER;

    /**
     * 设置全局 ObjectMapper (通常在 Spring 配置类中调用)
     */
    public static void setGlobalObjectMapper(ObjectMapper objectMapper) {
        OBJECT_MAPPER = objectMapper;
    }

    /**
     * 获取 ObjectMapper，包含空指针检查
     */
    private static ObjectMapper getMapper() {
        if (OBJECT_MAPPER == null) {
            throw new RuntimeException("全局 ObjectMapper 未注入，请检查 JacksonConfig 或手动调用 setGlobalObjectMapper");
        }
        return OBJECT_MAPPER;
    }

    // 简单类型白名单
    // 如果目标字段的类型在此列表中，直接赋值；否则视为 JSON 字符串进行反序列化处理
    private static final Set<Class<?>> SIMPLE_TYPES = new HashSet<>(Arrays.asList(
            String.class, Long.class, Integer.class, Short.class, Byte.class, Double.class, Float.class,
            Boolean.class, Character.class, BigDecimal.class, Date.class, LocalDate.class, LocalDateTime.class
    ));

    /**
     * 内部任务类：描述一个具体的"萃取"动作
     * 例如："去 User 表，根据 creator_id 查 user_name"
     */
    private static class Task<E, V> {
        // 任务唯一标识 (UUID片段)，用于在 UNION 查询结果中区分这一行属于哪个任务
        String sourceKey;
        // 目标表名 (自动解析)
        String tableName;
        // 关联键列名 (自动解析，如 user_id)
        String idCol;
        // 目标值列名 (自动解析，如 user_name)
        String targetCol;

        // 从源对象 (Entity) 中获取关联 ID 的函数 (支持 Long 或 String)
        Function<E, Object> entityIdGetter;

        // 结果处理器：将查询到的 String 值转换并回填到 VO
        BiConsumer<V, String> valueProcessor;

        // 收集到的所有 ID (用于生成 WHERE IN (...))
        Set<Object> collectedIds = new HashSet<>();
    }

    // 源实体列表
    private final List<E> entities;
    // 目标视图对象列表
    private final List<V> vos;
    // 执行动态 SQL 的 Mapper
    private final UniversalSearchMapper mapper;
    // 待执行的任务列表
    private final List<Task<E, V>> tasks = new ArrayList<>();

    /**
     * 私有构造：初始化时直接将 Entity 转换为 VO
     */
    private SmartUniversalExtractor(List<E> entities, Function<E, V> converter, UniversalSearchMapper mapper) {
        this.entities = entities;
        this.mapper = mapper;
        this.vos = new ArrayList<>(entities.size());
        // 预先转换 VO，后续的回填操作直接针对 vos 列表进行
        for (E entity : entities) {
            this.vos.add(converter.apply(entity));
        }
    }

    /**
     * 静态入口：创建一个萃取器
     *
     * @param entities  数据库查询出的原始实体列表
     * @param converter Entity -> VO 的转换函数
     * @param mapper    UniversalSearchMapper 接口实例
     */
    public static <E, V> SmartUniversalExtractor<E, V> of(List<E> entities, Function<E, V> converter, UniversalSearchMapper mapper) {
        return new SmartUniversalExtractor<>(entities, converter, mapper);
    }

    /**
     * 核心方法：注册一个萃取任务
     *
     * @param relatedClass      关联的目标实体类 (如 User.class)
     * @param relatedIdFunc     关联表的主键/关联键 Getter (如 User::getId)
     * @param relatedTargetFunc 关联表的目标字段 Getter (如 User::getName 或 User::getConfigJson)
     * @param mainIdFunc        主对象中持有外键的 Getter (如 Order::getCreatorId)，支持返回 String/Long
     * @param voSetter          VO 的回填 Setter (如 OrderVO::setCreatorName)
     * @param <R>               关联表类型
     * @param <T>               目标字段类型
     */
    public <R, T> SmartUniversalExtractor<E, V> add(
            Class<R> relatedClass,
            LambdaGetter<R> relatedIdFunc,
            LambdaGetter<R> relatedTargetFunc,
            Function<E, Object> mainIdFunc,
            BiConsumer<V, T> voSetter
    ) {
        // 1. 利用 MyBatis-Flex 获取表元数据 (无需手写表名)
        TableInfo tableInfo = TableInfoFactory.ofEntityClass(relatedClass);
        if (tableInfo == null) {
            throw new RuntimeException("无法获取 TableInfo: " + relatedClass.getName() + "，请检查 @Table 注解");
        }

        // 2. 解析返回类型，判断是否为复杂对象 (JSON)
        Class<?> returnType = resolveReturnType(relatedClass, relatedTargetFunc);
        boolean isComplexType = !SIMPLE_TYPES.contains(returnType) && !returnType.isPrimitive() && !returnType.isEnum();

        // 3. 构建任务
        Task<E, V> task = new Task<>();
        task.sourceKey = UUID.randomUUID().toString().substring(0, 8);
        task.tableName = tableInfo.getTableName();

        // 4. 解析列名 (Property -> Column)
        task.idCol = resolveColumn(tableInfo, relatedIdFunc);
        task.targetCol = resolveColumn(tableInfo, relatedTargetFunc);
        task.entityIdGetter = mainIdFunc;

        // 5. 定义值处理器 (当数据库返回值到来时如何处理)
        task.valueProcessor = (vo, rawDbValue) -> {
            if (rawDbValue == null) return;
            if (isComplexType) {
                // 复杂类型：反序列化 JSON
                try {
                    T complexObj = (T) getMapper().readValue(rawDbValue, returnType);
                    voSetter.accept(vo, complexObj);
                } catch (Exception e) {
                    // 生产环境建议改为 log.warn
                    e.printStackTrace();
                }
            } else {
                // 简单类型：直接强转
                voSetter.accept(vo, (T) rawDbValue);
            }
        };

        // 6. 立即遍历实体，收集需要的 ID
        for (E entity : entities) {
            Object id = mainIdFunc.apply(entity);
            // 过滤掉 null 和空字符串
            if (id != null && !id.toString().isEmpty()) {
                task.collectedIds.add(id);
            }
        }

        // 只有收集到了 ID，才将任务加入队列
        if (!task.collectedIds.isEmpty()) this.tasks.add(task);

        return this;
    }

    /**
     * 辅助：通过 Flex 解析列名
     */
    private <R> String resolveColumn(TableInfo tableInfo, LambdaGetter<R> func) {
        String property = LambdaUtil.getFieldName(func);
        String column = tableInfo.getColumnByProperty(property);
        if (column == null || column.isEmpty()) {
            throw new RuntimeException("无法解析列名: " + property + "，请确保实体类中包含该字段且未被忽略");
        }
        return column;
    }

    /**
     * 辅助：反射获取 Getter 返回类型
     */
    private <R> Class<?> resolveReturnType(Class<R> clazz, LambdaGetter<R> func) {
        try {
            String fieldName = LambdaUtil.getFieldName(func);
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            try {
                return clazz.getMethod(getterName).getReturnType();
            } catch (NoSuchMethodException e) {
                // 尝试 boolean 类型的 isXxx
                return clazz.getMethod("is" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1)).getReturnType();
            }
        } catch (Exception e) {
            throw new RuntimeException("无法通过反射解析返回类型", e);
        }
    }

    /**
     * 执行最终查询并回填
     *
     * @return 处理完毕的 VO 列表
     */
    public List<V> execute() {
        if (tasks.isEmpty()) return vos;

        StringBuilder sqlBuilder = new StringBuilder();

        // --- 1. 构建巨大的 UNION ALL SQL ---
        for (int i = 0; i < tasks.size(); i++) {
            Task<E, V> task = tasks.get(i);

            // 处理 IN (...) 中的值：如果是 String 需要加单引号，如果是 Number 则不需要
            String idsStr = task.collectedIds.stream().map(id -> {
                if (id instanceof Number) {
                    return String.valueOf(id);
                } else {
                    // 简单防注入处理，替换单引号
                    return "'" + id.toString().replace("'", "''") + "'";
                }
            }).collect(Collectors.joining(","));

            if (i > 0) sqlBuilder.append(" UNION ALL ");

            // 核心 SQL 模板:
            // SELECT 'uuid' as s, id_col as k, target_col as v FROM table WHERE id_col IN (...)

            // 兼容性处理：
            // PostgreSQL 使用 ::text 强转
            // MySQL 使用 CAST(... AS CHAR)
            // 必须强转的原因：UNION ALL 要求所有子查询的对应列类型一致。
            // 如果 Task A 查 Long ID，Task B 查 String ID，不强转会报错。

            // 下面是 PostgreSQL 写法 (如果用 MySQL，请替换注释中的代码)
            String keySql = task.idCol + "::text";       // MySQL: "CAST(" + task.idCol + " AS CHAR)"
            String valSql = task.targetCol + "::text";   // MySQL: "CAST(" + task.targetCol + " AS CHAR)"

            sqlBuilder.append(String.format(
                    "SELECT '%s' as s, %s as k, %s as v FROM %s WHERE %s IN (%s)",
                    task.sourceKey,
                    keySql,
                    valSql,
                    task.tableName,
                    task.idCol,
                    idsStr
            ));
        }

        // --- 2. 执行物理查询 ---
        // 结果集每一行包含：s (SourceKey), k (ID), v (Value)
        List<Map<String, Object>> results = mapper.executeDynamicUnionQuery(sqlBuilder.toString());

        // --- 3. 内存重组 ---
        // 将扁平结果转为 Map<SourceKey, Map<ID, Value>> 方便 O(1) 查找
        Map<String, Map<String, String>> resultMap = new HashMap<>();
        for (Map<String, Object> row : results) {
            String s = String.valueOf(row.get("s"));
            String k = String.valueOf(row.get("k")); // 数据库取出的 key 统一视为 String
            String v = String.valueOf(row.get("v"));

            resultMap.computeIfAbsent(s, key -> new HashMap<>()).put(k, v);
        }

        // --- 4. 回填 VO ---
        for (int i = 0; i < entities.size(); i++) {
            E entity = entities.get(i);
            V vo = vos.get(i);

            for (Task<E, V> task : tasks) {
                // 获取当前实体对应的外键 ID
                Object idObj = task.entityIdGetter.apply(entity);
                if (idObj != null) {
                    // 统一转 String 进行 Map 查找
                    String idStr = String.valueOf(idObj);

                    if (resultMap.containsKey(task.sourceKey)) {
                        String val = resultMap.get(task.sourceKey).get(idStr);
                        // 调用 Setter
                        task.valueProcessor.accept(vo, val);
                    }
                }
            }
        }
        return vos;
    }
}
