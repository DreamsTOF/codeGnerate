package com.dream.codegenerate.utils;

import cn.hutool.core.lang.func.Func1;
import cn.hutool.core.lang.func.LambdaUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.json.JSONUtil;
import com.dream.codegenerate.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * SmartValidator - 工业级流式校验工具 (Caffeine 缓存增强版)
 * <p>
 * 特性：
 * 1. O(1) 复杂度，支持本地二级缓存 (Caffeine) 存储 Lambda 解析结果
 * 2. 自动通过 Lambda 提取字段名，彻底告别硬编码提示语
 * 3. 适配 Hutool 5.8.42 稳定版，使用 Func1 接口
 * 4. 深度集成项目 ErrorCode 与 ThrowUtils，实现快速失败 (Fail-Fast)
 * </p>
 *
 * @param <T> DTO类型
 */
public class SmartValidator<T> {

    // ========================================================================
    // 本地二级缓存：Key 为 Lambda 的 Class，Value 为解析出的字段名字符串
    // ========================================================================
    private static final Cache<Class<?>, String> FIELD_NAME_CACHE = Caffeine.newBuilder()
            .initialCapacity(256)
            .maximumSize(2048)
            .expireAfterAccess(1, TimeUnit.DAYS)
            .build();

    private final T dto;
    private Object currentFieldValue;
    private String currentFieldName;
    private boolean hasValue = false;
    private boolean isSkipped = false;

    private SmartValidator(T dto) {
        this.dto = dto;
    }

    /**
     * 初始化校验器
     */
    public static <T> SmartValidator<T> of(T dto) {
        ThrowUtils.throwIf(dto == null, ErrorCode.PARAM_IS_NULL, "校验对象不可为空");
        return new SmartValidator<>(dto);
    }

    // ========================================================================
    // 核心重载：必填字段 (Required Fields)
    // ========================================================================

    /**
     * 基础校验：定位字段并校验必填
     * 自动处理：String(blank), Collection(empty), Map(empty), Array(empty)
     */
    public <R> SmartValidator<T> field(Func1<T, R> getter) {
        this.resetContext(getter);
        ThrowUtils.throwIf(!hasValue, ErrorCode.PARAM_IS_NULL, currentFieldName + " 不能为空");
        return this;
    }

    /**
     * 数值/对象范围校验：必填 + [min, max] 范围
     */
    @SuppressWarnings("unchecked")
    public <R extends Comparable<R>> SmartValidator<T> field(Func1<T, R> getter, R min, R max) {
        this.field(getter);
        R value = (R) currentFieldValue;
        boolean outOfRange = value.compareTo(min) < 0 || value.compareTo(max) > 0;
        ThrowUtils.throwIf(outOfRange, ErrorCode.PARAM_OUT_OF_RANGE,
                currentFieldName + " 超出范围 [" + min + ", " + max + "]");
        return this;
    }

    /**
     * 业务逻辑校验：必填 + 自定义谓词
     */
    @SuppressWarnings("unchecked")
    public <R> SmartValidator<T> field(Func1<T, R> getter, Predicate<R> validator, ErrorCode errorCode, String msgSuffix) {
        this.field(getter);
        ThrowUtils.throwIf(!validator.test((R) currentFieldValue), errorCode, currentFieldName + " " + msgSuffix);
        return this;
    }

    /**
     * 集合/字符串长度限制校验
     */
    public <R> SmartValidator<T> fieldSize(Func1<T, R> getter, int min, int max) {
        this.field(getter);
        int size = this.calculateSize(currentFieldValue);
        boolean outOfRange = size < min || size > max;
        ThrowUtils.throwIf(outOfRange, ErrorCode.PARAM_OUT_OF_RANGE,
                currentFieldName + " 长度/大小必须在 [" + min + ", " + max + "] 之间");
        return this;
    }

    // ========================================================================
    // 核心重载：选填字段 (Optional Fields)
    // ========================================================================

    /**
     * 选填标记：如果字段为空则跳过后续链式校验
     */
    public <R> SmartValidator<T> optional(Func1<T, R> getter) {
        this.resetContext(getter);
        this.isSkipped = !hasValue;
        return this;
    }

    // ========================================================================
    // 快捷格式校验 (必须在 field 或 optional 之后调用)
    // ========================================================================

    /**
     * 校验当前字段是否为合法 JSON
     */
    public SmartValidator<T> isJson() {
        if (!isSkipped && hasValue && currentFieldValue instanceof String json) {
            ThrowUtils.throwIf(!JSONUtil.isTypeJSON(json), ErrorCode.JSON_PARSE_ERROR, currentFieldName + " 格式非法");
        }
        return this;
    }

    /**
     * 校验当前字段是否匹配正则
     */
    public SmartValidator<T> match(String regex, String errorMsg) {
        if (!isSkipped && hasValue && currentFieldValue instanceof String str) {
            ThrowUtils.throwIf(!ReUtil.isMatch(regex, str), ErrorCode.PARAM_FORMAT_ERROR, currentFieldName + " " + errorMsg);
        }
        return this;
    }

    /**
     * 校验当前字段是否为邮箱
     */
    public SmartValidator<T> isEmail() {
        return match("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$", "格式不是有效的邮箱");
    }

    /**
     * 校验当前字段是否为手机号
     */
    public SmartValidator<T> isPhone() {
        return match("^1[3-9]\\d{9}$", "格式不是有效的手机号");
    }

    // ========================================================================
    // 内部辅助
    // ========================================================================

    private <R> void resetContext(Func1<T, R> getter) {
        // 先从缓存拿字段名，拿不到再去解析
        this.currentFieldName = FIELD_NAME_CACHE.get(getter.getClass(), k -> LambdaUtil.getFieldName(getter));

        // 执行 Lambda 获取值
        this.currentFieldValue = getter.callWithRuntimeException(dto);

        // 自动判定非空 (支持 String/Collection/Map 等)
        this.hasValue = ObjectUtil.isNotEmpty(currentFieldValue);
        this.isSkipped = false;
    }

    private int calculateSize(Object value) {
        if (value instanceof CharSequence cs) return cs.length();
        if (value instanceof Collection<?> c) return c.size();
        if (value instanceof Map<?, ?> m) return m.size();
        if (value instanceof Object[] a) return a.length;
        return 0;
    }
}
