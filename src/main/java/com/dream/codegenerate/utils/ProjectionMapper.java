package com.dream.codegenerate.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.lang.func.LambdaUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.TypeUtil;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.utils.ThrowUtils;
import com.dream.codegenerate.utils.VoidFunc2;


import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ProjectionMapper - 声明式对象转换与映射工具
 * 支持自动类型转换、基础属性拷贝、以及带参数的方法引用
 */
public class ProjectionMapper<S, T> {

    private final S source;
    private final T target;

    private ProjectionMapper(S source, T targetInstance) {
        this.source = source;
        this.target = targetInstance;
        // 默认执行基础属性拷贝，补全名称和类型一致的字段
        BeanUtil.copyProperties(source, target);
    }

    // ============================================================
    // 静态入口：支持 Function (带参数的方法引用，如 Hzqsmzq_tbzy::toHzqsmzq)
    // ============================================================

    public static <S, T> ProjectionMapper<S, T> of(S source, Function<S, T> transformer) {
        ThrowUtils.throwIf(source == null, ErrorCode.PARAM_IS_NULL, "源对象不可为空");
        return new ProjectionMapper<>(source, transformer.apply(source));
    }

    public static <S, T> T convert(S source, Function<S, T> transformer) {
        return convert(source, transformer, null);
    }

    public static <S, T> T convert(S source, Function<S, T> transformer, Consumer<ProjectionMapper<S, T>> action) {
        ProjectionMapper<S, T> mapper = of(source, transformer);
        if (action != null) action.accept(mapper);
        return mapper.build();
    }

    public static <S, T> List<T> toList(List<S> sources, Function<S, T> transformer) {
        return toList(sources, transformer, null);
    }

    public static <S, T> List<T> toList(List<S> sources, Function<S, T> transformer, Consumer<ProjectionMapper<S, T>> action) {
        if (CollUtil.isEmpty(sources)) return Collections.emptyList();
        List<T> result = new ArrayList<>(sources.size());
        for (S s : sources) result.add(convert(s, transformer, action));
        return result;
    }

    // ============================================================
    // 静态入口：支持 Supplier (无参数的方法引用，如 Hzqsmzq::new)
    // ============================================================

    public static <S, T> ProjectionMapper<S, T> of(S source, Supplier<T> targetCreator) {
        ThrowUtils.throwIf(source == null, ErrorCode.PARAM_IS_NULL, "源对象不可为空");
        return new ProjectionMapper<>(source, targetCreator.get());
    }

    public static <S, T> T convert(S source, Supplier<T> targetCreator) {
        return convert(source, targetCreator, null);
    }

    public static <S, T> T convert(S source, Supplier<T> targetCreator, Consumer<ProjectionMapper<S, T>> action) {
        ProjectionMapper<S, T> mapper = of(source, targetCreator);
        if (action != null) action.accept(mapper);
        return mapper.build();
    }

    public static <S, T> List<T> toList(List<S> sources, Supplier<T> targetCreator) {
        return toList(sources, targetCreator, null);
    }

    public static <S, T> List<T> toList(List<S> sources, Supplier<T> targetCreator, Consumer<ProjectionMapper<S, T>> action) {
        if (CollUtil.isEmpty(sources)) return Collections.emptyList();
        List<T> result = new ArrayList<>(sources.size());
        for (S s : sources) result.add(convert(s, targetCreator, action));
        return result;
    }

    // ============================================================
    // 核心映射逻辑
    // ============================================================

    /**
     * 自动转换映射：利用 Hutool Convert 自动处理类型不匹配
     */
    public <P1, P2> ProjectionMapper<S, T> map(Func1<S, P1> sourceGetter, VoidFunc2<T, P2> targetSetter) {
        P1 sourceValue = sourceGetter.callWithRuntimeException(source);
        if (sourceValue != null) {
            Class<?> targetParamType = getSetterType(targetSetter);
            Object convertedValue = Convert.convert(targetParamType, sourceValue);
            try {
                @SuppressWarnings("unchecked")
                P2 finalValue = (P2) convertedValue;
                targetSetter.apply(target, finalValue);
            } catch (Exception ignored) {}
        }
        return this;
    }

    /**
     * 自定义逻辑映射：支持显式的转换函数
     */
    public <P1, R> ProjectionMapper<S, T> map(Func1<S, P1> sourceGetter, Function<P1, R> transformer, VoidFunc2<T, R> targetSetter) {
        P1 sourceValue = sourceGetter.callWithRuntimeException(source);
        if (sourceValue != null) {
            R transformedValue = transformer.apply(sourceValue);
            try {
                targetSetter.apply(target, transformedValue);
            } catch (Exception ignored) {}
        }
        return this;
    }

    public T build() {
        return target;
    }

    /**
     * 解析 Setter 对应的字段类型 (采用用户提供的 TypeUtil 方案)
     */
    private Class<?> getSetterType(VoidFunc2<T, ?> setter) {
        // 获取 VoidFunc2<T, P> 中的 P 的实际类型
        // 注意：这只在 Lambda 表达式有明确类型推导时有效
        Type type = TypeUtil.getTypeArgument(setter.getClass(), 1);
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        return Object.class;
    }

    /**
     * 并行集合转换（利用 Parallel Stream 提升海量数据效率）
     */
    public static <S, T> List<T> toParallelList(List<S> sources, Function<S, T> transformer, Consumer<ProjectionMapper<S, T>> action) {
        if (CollUtil.isEmpty(sources)) return Collections.emptyList();

        // 使用 parallelStream 开启多线程并行处理
        return sources.parallelStream()
                .map(s -> convert(s, transformer, action))
                .collect(java.util.stream.Collectors.toList());
    }
}
