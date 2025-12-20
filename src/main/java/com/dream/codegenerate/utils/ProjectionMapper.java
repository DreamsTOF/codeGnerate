package com.dream.codegenerate.utils;

import cn.hutool.v7.core.bean.BeanUtil;
import cn.hutool.v7.core.collection.CollUtil;
import cn.hutool.v7.core.convert.ConvertUtil;
import cn.hutool.v7.core.func.LambdaUtil;
import cn.hutool.v7.core.func.SerBiConsumer;
import cn.hutool.v7.core.func.SerFunction;
import cn.hutool.v7.core.reflect.ConstructorUtil;
import cn.hutool.v7.core.reflect.FieldUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * ProjectionMapper - 声明式对象转换与映射工具
 * <p>
 * 针对 Hutool 7.0.0 优化：
 * 1. 深度集成 Hutool Convert，实现类型自动转换。
 * 2. 强类型安全，利用 Lambda 表达式在编译期锁定字段。
 * 3. 支持批量与并行转换。
 */
public class ProjectionMapper<S, T> {

    private final S source;
    private final T target;

    // 私有构造：通过类反射创建实例
    private ProjectionMapper(S source, Class<T> targetClass) {
        this.source = source;
        this.target = ConstructorUtil.newInstance(targetClass);
        BeanUtil.copyProperties(source, target);
    }

    // 私有构造：通过已有实例创建（支持 Supplier/Function 模式）
    private ProjectionMapper(S source, T targetInstance) {
        this.source = source;
        this.target = targetInstance;
        BeanUtil.copyProperties(source, target);
    }

    // ============================================================
    // 静态入口
    // ============================================================

    /** 基础入口：指定目标类 */
    public static <S, T> ProjectionMapper<S, T> of(S source, Class<T> targetClass) {
        if (source == null) throw new RuntimeException("源对象不可为空");
        return new ProjectionMapper<>(source, targetClass);
    }

    /** 灵活入口：支持 Supplier (如 User::new) */
    public static <S, T> ProjectionMapper<S, T> of(S source, Supplier<T> targetCreator) {
        if (source == null) throw new RuntimeException("源对象不可为空");
        return new ProjectionMapper<>(source, targetCreator.get());
    }

    /** 灵活入口：支持 Function (带参数的构造或预处理) */
    public static <S, T> ProjectionMapper<S, T> of(S source, Function<S, T> transformer) {
        if (source == null) throw new RuntimeException("源对象不可为空");
        return new ProjectionMapper<>(source, transformer.apply(source));
    }

    // ============================================================
    // 单个对象便捷转换
    // ============================================================

    public static <S, T> T convert(S source, Supplier<T> targetCreator, Consumer<ProjectionMapper<S, T>> action) {
        ProjectionMapper<S, T> mapper = of(source, targetCreator);
        if (action != null) action.accept(mapper);
        return mapper.build();
    }

    public static <S, T> T convert(S source, Function<S, T> transformer, Consumer<ProjectionMapper<S, T>> action) {
        ProjectionMapper<S, T> mapper = of(source, transformer);
        if (action != null) action.accept(mapper);
        return mapper.build();
    }

    // ============================================================
    // 集合转换 (List)
    // ============================================================

    public static <S, T> List<T> toList(List<S> sources, Supplier<T> targetCreator, Consumer<ProjectionMapper<S, T>> action) {
        if (CollUtil.isEmpty(sources)) return Collections.emptyList();
        List<T> result = new ArrayList<>(sources.size());
        for (S s : sources) result.add(convert(s, targetCreator, action));
        return result;
    }

    public static <S, T> List<T> toList(List<S> sources, Function<S, T> transformer, Consumer<ProjectionMapper<S, T>> action) {
        if (CollUtil.isEmpty(sources)) return Collections.emptyList();
        List<T> result = new ArrayList<>(sources.size());
        for (S s : sources) result.add(convert(s, transformer, action));
        return result;
    }

    /**
     * 并行集合转换（利用 Parallel Stream 提升海量数据效率）
     */
    public static <S, T> List<T> toParallelList(List<S> sources, Function<S, T> transformer, Consumer<ProjectionMapper<S, T>> action) {
        if (CollUtil.isEmpty(sources)) return Collections.emptyList();
        return sources.parallelStream()
                .map(s -> convert(s, transformer, action))
                .collect(Collectors.toList());
    }

    // ============================================================
    // 核心映射逻辑 (流式调用)
    // ============================================================

    /**
     * 自动转换映射：利用 Hutool v7 ConvertUtil 自动处理类型不匹配
     */
    public <P1, P2> ProjectionMapper<S, T> map(SerFunction<S, P1> sourceGetter, SerBiConsumer<T, P2> targetSetter) {
        P1 sourceValue = sourceGetter.apply(source);
        if (sourceValue != null) {
            Class<?> targetParamType = getSetterType(targetSetter);
            Object convertedValue = ConvertUtil.convert(targetParamType, sourceValue);

            @SuppressWarnings("unchecked")
            P2 finalValue = (P2) convertedValue;
            targetSetter.accept(target, finalValue);
        }
        return this;
    }

    /**
     * 自定义逻辑映射：支持显式的转换函数
     */
    public <P1, R> ProjectionMapper<S, T> map(SerFunction<S, P1> sourceGetter, Function<P1, R> transformer, SerBiConsumer<T, R> targetSetter) {
        P1 sourceValue = sourceGetter.apply(source);
        if (sourceValue != null) {
            R transformedValue = transformer.apply(sourceValue);
            targetSetter.accept(target, transformedValue);
        }
        return this;
    }

    public T build() {
        return target;
    }

    /**
     * 解析 Setter 对应的字段类型
     */
    private Class<?> getSetterType(SerBiConsumer<T, ?> setter) {
        // v7 中 LambdaUtil.getFieldName 依然依赖序列化 Lambda
        String fieldName = LambdaUtil.getFieldName(setter);
        return FieldUtil.getField(target.getClass(), fieldName).getType();
    }
}
