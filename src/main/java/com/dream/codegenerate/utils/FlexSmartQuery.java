package com.dream.codegenerate.utils;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.mybatis.Mappers;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.query.QueryMethods;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.mybatisflex.core.util.LambdaGetter;
import com.mybatisflex.core.util.LambdaUtil;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 极速查询构建器 (针对 JDK 25 & Hutool v7 极致精简版)
 * <p>
 * -------------------------------------------------------------------------
 * 💡 使用示例 (Usage Examples):
 * <p>
 * 1. [全自动模式] - 只要 DTO 字段名和 Entity 属性一致，且非空才生效 (String 会自动进行 isNotBlank 检查)
 * List<User> list = FlexSmartQuery.of(User.class)
 * .autoBuild(userDto)
 * .list();
 * <p>
 * 2. [局部定制模式] - 覆盖默认行为（例如：年龄用 >=，姓名用精确匹配，字段名不一致时指定列）
 * List<User> list = FlexSmartQuery.of(User.class)
 * .match(UserDTO::getAge, MatchType.GE)           // age 自动匹配 USER.AGE，匹配方式设为 >=
 * .match(UserDTO::getName, MatchType.EQ)          // name 强制改为精准匹配 (默认是 LIKE)
 * .match(UserDTO::getKeyword, USER.REMARK)       // 手动指定 DTO 字段映射到数据库的某个列
 * .autoBuild(userDto)
 * .list();
 * <p>
 * 3. [聚合回填模式] - 在查询的同时，统计信息直接塞进 ResponseDTO 的非数据库字段
 * UserResponse res = new UserResponse();
 * Page<User> page = FlexSmartQuery.of(User.class)
 * .autoBuild(queryDto)
 * .count(res::setTotalCount)                      // 执行 select count(*) 并填充到 res
 * .sum(USER.MONEY, res::setTotalMoney)            // 执行 select sum(money) 并填充到 res
 * .page(queryDto.getPage());
 * <p>
 * 4. [高级自定义模式] - 当一个查询字段需要关联多个数据库列时，使用 Lambda 自定义逻辑
 * // 场景：前端传一个 keyword，后端需要同时模糊匹配 username 或 nickname
 * query.match(UserDTO::getSearch, USER.NAME, (column, value) ->
 * column.like(value).or(USER.NICKNAME.like(value))
 * )
 * .autoBuild(dto);
 * -------------------------------------------------------------------------
 */
public class FlexSmartQuery<E> {

    /** 目标实体类 Class */
    private final Class<E> entityClass;
    /** MyBatis-Flex 原生查询包装器 */
    private final QueryWrapper queryWrapper;
    /** 目标实体的表元数据信息 */
    private final TableInfo tableInfo;

    /** 存储用户手动配置的字段匹配规则覆盖 */
    private final Map<String, Rule> overrides = new HashMap<>();

    /**
     * 私有构造函数，初始化表信息和初始 Wrapper
     * @param entityClass 实体类 Class
     */
    private FlexSmartQuery(Class<E> entityClass) {
        this.entityClass = entityClass;
        this.tableInfo = TableInfoFactory.ofEntityClass(entityClass);
        this.queryWrapper = QueryWrapper.create().from(tableInfo.getTableName());
    }

    /**
     * 静态工厂方法，创建一个查询构建器实例
     * @param entityClass 实体类 Class
     * @param <E> 实体类型
     * @return 构建器实例
     */
    public static <E> FlexSmartQuery<E> of(Class<E> entityClass) {
        return new FlexSmartQuery<>(entityClass);
    }

    /**
     * 匹配类型枚举定义
     */
    public enum MatchType {
        /** 等于 = */
        EQ,
        /** 不等于 != */
        NE,
        /** 大于等于 >= */
        GE,
        /** 大于 > */
        GT,
        /** 小于等于 <= */
        LE,
        /** 小于 < */
        LT,
        /** 全模糊 LIKE %val% */
        LIKE,
        /** 左模糊 LIKE %val */
        LEFT_LIKE,
        /** 右模糊 LIKE val% */
        RIGHT_LIKE,
        /** 集合查询 IN (...) */
        IN,
        /** 自定义逻辑 */
        CUSTOM
    }

    /**
     * 内部规则载体，记录列、匹配类型以及自定义函数
     */
    private record Rule(QueryColumn column, MatchType type, BiFunction<QueryColumn, Object, QueryCondition> customFn) {}

    // ========================================================================
    // 聚合与统计增强 (Aggregation & Statistics)
    // ========================================================================

    /**
     * 统计当前条件下的总记录数，并通过 Setter 填充结果
     * @param setter 接收 Long 类型的 Consumer (如 dto::setTotal)
     * @return 当前构建器实例
     */
    public FlexSmartQuery<E> count(Consumer<Long> setter) {
        long total = Mappers.ofEntityClass(entityClass).selectCountByQuery(queryWrapper);
        setter.accept(total);
        return this;
    }

    /**
     * 对指定列执行求和运算 (SUM)，并通过 Setter 填充结果
     * @param column 数据库列
     * @param setter 接收数值类型的 Consumer
     * @param <N> 数值类型
     * @return 当前构建器实例
     */
    public <N extends Number> FlexSmartQuery<E> sum(QueryColumn column, Consumer<N> setter) {
        QueryWrapper sumWrapper = queryWrapper.clone()
                .select(QueryMethods.sum(column).as("sum_val"));
        Object result = Mappers.ofEntityClass(entityClass).selectObjectByQuery(sumWrapper);
        return safeInvoke(setter, result);
    }

    /**
     * 对指定列执行平均值运算 (AVG)，并通过 Setter 填充结果
     * @param column 数据库列
     * @param setter 接收数值类型的 Consumer
     * @param <N> 数值类型
     * @return 当前构建器实例
     */
    public <N extends Number> FlexSmartQuery<E> avg(QueryColumn column, Consumer<N> setter) {
        QueryWrapper avgWrapper = queryWrapper.clone()
                .select(QueryMethods.avg(column).as("avg_val"));
        Object result = Mappers.ofEntityClass(entityClass).selectObjectByQuery(avgWrapper);
        return safeInvoke(setter, result);
    }

    /**
     * 获取指定列的最大值 (MAX)，并通过 Setter 填充结果
     * @param column 数据库列
     * @param setter 接收值的 Consumer
     * @param <V> 值类型
     * @return 当前构建器实例
     */
    public <V> FlexSmartQuery<E> max(QueryColumn column, Consumer<V> setter) {
        QueryWrapper maxWrapper = queryWrapper.clone()
                .select(QueryMethods.max(column).as("max_val"));
        Object result = Mappers.ofEntityClass(entityClass).selectObjectByQuery(maxWrapper);
        return safeInvoke(setter, result);
    }

    /**
     * 内部辅助方法：集中处理未经检查的转换，并执行 Setter 调用
     * @param setter 目标 Setter
     * @param value 聚合查询结果
     * @return 当前构建器实例
     */
    @SuppressWarnings("unchecked")
    private <T> FlexSmartQuery<E> safeInvoke(Consumer<T> setter, Object value) {
        if (value != null && setter != null) {
            setter.accept((T) value);
        }
        return this;
    }

    // ========================================================================
    // 链式规则配置 (Fluent API)
    // ========================================================================

    /**
     * 指定某个 DTO 字段的匹配方式（列名保持默认按名称推导）
     * @param getter DTO 字段的 Getter 方法引用
     * @param type 匹配类型
     * @return 当前构建器实例
     */
    public <D> FlexSmartQuery<E> match(LambdaGetter<D> getter, MatchType type) {
        String fieldName = LambdaUtil.getFieldName(getter);
        overrides.put(fieldName, new Rule(null, type, null));
        return this;
    }

    /**
     * 指定某个 DTO 字段对应的数据库列（匹配方式保持默认推导）
     * @param getter DTO 字段的 Getter 方法引用
     * @param column 指定的数据库列
     * @return 当前构建器实例
     */
    public <D> FlexSmartQuery<E> match(LambdaGetter<D> getter, QueryColumn column) {
        String fieldName = LambdaUtil.getFieldName(getter);
        overrides.put(fieldName, new Rule(column, null, null));
        return this;
    }

    /**
     * 同时指定 DTO 字段对应的数据库列和匹配方式
     * @param getter DTO 字段的 Getter 方法引用
     * @param column 指定的数据库列
     * @param type 匹配类型
     * @return 当前构建器实例
     */
    public <D> FlexSmartQuery<E> match(LambdaGetter<D> getter, QueryColumn column, MatchType type) {
        String fieldName = LambdaUtil.getFieldName(getter);
        overrides.put(fieldName, new Rule(column, type, null));
        return this;
    }

    /**
     * 高级自定义匹配：提供列和值，由用户决定如何生成 QueryCondition
     * @param getter DTO 字段的 Getter 方法引用
     * @param column 指定的数据库主列（会传给 fn 第一个参数）
     * @param fn 自定义逻辑函数：(QueryColumn, ObjectValue) -> QueryCondition
     * @return 当前构建器实例
     */
    public <D> FlexSmartQuery<E> match(LambdaGetter<D> getter, QueryColumn column, BiFunction<QueryColumn, Object, QueryCondition> fn) {
        String fieldName = LambdaUtil.getFieldName(getter);
        overrides.put(fieldName, new Rule(column, MatchType.CUSTOM, fn));
        return this;
    }

    // ========================================================================
    // 构建逻辑
    // ========================================================================

    /**
     * 核心构建方法：扫描 DTO 属性，结合 Overrides 规则自动构建 QueryWrapper
     * @param dto 查询参数对象
     * @return 当前构建器实例
     */
    public <D> FlexSmartQuery<E> autoBuild(D dto) {
        if (dto == null) return this;
        // 获取 DTO 声明的所有字段
        var fields = ReflectUtil.getFields(dto.getClass());
        for (var field :fields) {
            String fieldName = field.getName();
            Object value = ReflectUtil.getFieldValue(dto, field);
            // 1. 智能有效性校验 (判空/判白/判集合空)
            if (!isValid(value)) continue;

            // 2. 查找是否有手动配置的规则覆盖
            Rule rule = overrides.get(fieldName);

            // 确定数据库列：显式指定优先，否则按名在实体类中查找
            QueryColumn column = (rule != null && rule.column != null)
                    ? rule.column
                    : tableInfo.getQueryColumnByProperty(fieldName);

            if (column == null) continue;

            // 确定匹配类型：显式指定优先，否则根据值类型推导默认策略
            MatchType type = (rule != null && rule.type != null) ? rule.type : determineDefaultType(value);

            // 3. 应用条件：如果是自定义逻辑则执行自定义函数，否则走默认映射
            if (type == MatchType.CUSTOM && rule != null && rule.customFn != null) {
                queryWrapper.and(rule.customFn.apply(column, value));
            } else {
                applyDefaultCondition(column, value, type);
            }
        }
        return this;
    }

    /**
     * 内部方法：将常用匹配类型转换为 MyBatis-Flex 条件
     */
    private void applyDefaultCondition(QueryColumn column, Object value, MatchType type) {
        switch (type) {
            case EQ -> queryWrapper.and(column.eq(value));
            case NE -> queryWrapper.and(column.ne(value));
            case GE -> queryWrapper.and(column.ge(value));
            case GT -> queryWrapper.and(column.gt(value));
            case LE -> queryWrapper.and(column.le(value));
            case LT -> queryWrapper.and(column.lt(value));
            case LIKE -> queryWrapper.and(column.like(value));
            case LEFT_LIKE -> queryWrapper.and(column.likeLeft(value));
            case RIGHT_LIKE -> queryWrapper.and(column.likeRight(value));
            case IN -> {
                if (value instanceof Collection<?> coll) queryWrapper.and(column.in(coll));
                else queryWrapper.and(column.eq(value));
            }
        }
    }

    // ========================================================================
    // 终端操作 (Terminal Operations)
    // ========================================================================

    /**
     * 执行查询并返回结果列表
     * @return 实体列表
     */
    public List<E> list() {
        return Mappers.ofEntityClass(entityClass).selectListByQuery(queryWrapper);
    }

    /**
     * 执行分页查询
     * @param pageNumber 当前页码 (1-based)
     * @param pageSize 每页记录数
     * @return 分页结果对象
     */
    public Page<E> page(int pageNumber, int pageSize) {
        return Mappers.ofEntityClass(entityClass).paginate(pageNumber, pageSize, queryWrapper);
    }

    /**
     * 执行分页查询 (基于现有 Page 对象)
     * @param page 分页参数
     * @return 分页结果对象
     */
    public Page<E> page(Page<E> page) {
        return Mappers.ofEntityClass(entityClass).paginate(page, queryWrapper);
    }

    /**
     * 指定查询返回的列（默认为 select *）
     * @param columns 列清单
     * @return 当前构建器实例
     */
    public FlexSmartQuery<E> select(QueryColumn... columns) {
        queryWrapper.select(columns);
        return this;
    }

    /**
     * 指定排序规则（默认为升序）
     * @param columns 排序字段清单
     * @return 当前构建器实例
     */
    public FlexSmartQuery<E> orderBy(QueryColumn... columns) {
        if (columns != null) {
            for (QueryColumn col : columns) queryWrapper.orderBy(col.asc());
        }
        return this;
    }

    /**
     * 核心校验方法：利用 JDK 25 的模式匹配处理不同类型的“空值”
     * @param value 待校验的对象
     * @return 是否为有效参数
     */
    private boolean isValid(Object value) {
        return switch (value) {
            case null -> false;
            case String s -> StrUtil.isNotBlank(s); // 过滤空白字符
            case Collection<?> c -> CollUtil.isNotEmpty(c); // 过滤空集合
            case Map<?, ?> m -> CollUtil.isNotEmpty(m); // 过滤空 Map
            default -> true;
        };
    }

    /**
     * 默认策略推导：String 默认 LIKE，集合默认 IN，其他默认 EQ
     * @param value 参数值
     * @return 匹配类型
     */
    private MatchType determineDefaultType(Object value) {
        return switch (value) {
            case String _ -> MatchType.LIKE;
            case Collection<?> _ -> MatchType.IN;
            default -> MatchType.EQ;
        };
    }
}
