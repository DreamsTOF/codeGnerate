package com.dream.codegenerate.utils;

import cn.hutool.v7.core.func.LambdaUtil;
import cn.hutool.v7.core.func.SerFunction;
import cn.hutool.v7.core.lang.Validator;
import cn.hutool.v7.core.regex.ReUtil;
import cn.hutool.v7.core.util.ObjUtil;
import cn.hutool.v7.core.text.StrUtil;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Asserts - 轻量级断言工具 (替代原 SmartValidator)
 * <p>
 * 核心思想：
 * 1. 只有在校验失败时，才进行 Lambda 解析 (获取字段名)，正常路径无性能损耗。
 * 2. 纯静态方法，拒绝创建中间对象。
 * 3. 深度集成 ErrorCode。
 * </p>
 */
public class Asserts {

    // ========================================================================
    // 1. 基础非空校验
    // ========================================================================

    /**
     * 断言字段非空 (null, "", "  ", [], map={}, 0长度数组 均视为空)
     * <p>
     * 用法: Asserts.notEmpty(user, User::getName);
     */
    public static <T, R> void notEmpty(T obj, SerFunction<T, R> getter) {
        R value = getter.apply(obj);
        if (ObjUtil.isEmpty(value)) {
            throwException(getter, ErrorCode.PARAM_IS_NULL, "不能为空");
        }
    }

    /**
     * 断言对象非 null
     */
    public static <T, R> void notNull(T obj, SerFunction<T, R> getter) {
        R value = getter.apply(obj);
        if (value == null) {
            throwException(getter, ErrorCode.PARAM_IS_NULL, "不能为Null");
        }
    }

    // ========================================================================
    // 2. 业务逻辑校验
    // ========================================================================

    /**
     * 断言表达式为 True
     * <p>
     * 用法: Asserts.isTrue(user.getAge() > 18, ErrorCode.PARAM_VALUE_INVALID, "年龄必须大于18");
     */
    public static void isTrue(boolean expression, ErrorCode errorCode, String message) {
        if (!expression) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 断言表达式为 True (使用 Lambda 获取字段名作为前缀)
     * <p>
     * 用法: Asserts.isTrue(user, User::getAge, age -> age > 18, "必须大于18岁");
     */
    public static <T, R> void isTrue(T obj, SerFunction<T, R> getter, java.util.function.Predicate<R> predicate, String msgSuffix) {
        R value = getter.apply(obj);
        if (!predicate.test(value)) {
            throwException(getter, ErrorCode.PARAM_VALUE_INVALID, msgSuffix);
        }
    }

    /**
     * 断言两个值相等
     */
    public static <T, R> void equals(T obj, SerFunction<T, R> getter, R expected) {
        R value = getter.apply(obj);
        if (!ObjUtil.equals(value, expected)) {
            throwException(getter, ErrorCode.PARAM_VALUE_INVALID, "必须等于 " + expected);
        }
    }

    // ========================================================================
    // 3. 数值与范围
    // ========================================================================

    /**
     * 断言数值/对象在范围内 [min, max] (闭区间)
     */
    public static <T, R extends Comparable<R>> void range(T obj, SerFunction<T, R> getter, R min, R max) {
        R value = getter.apply(obj);
        // 如果值为 null，视作不满足范围 (或者根据业务需求，null 可能直接跳过，这里默认严格模式)
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throwException(getter, ErrorCode.PARAM_OUT_OF_RANGE,
                    StrUtil.format("必须在 [{}, {}] 之间", min, max));
        }
    }

    /**
     * 断言字符串/集合长度
     */
    public static <T, R> void length(T obj, SerFunction<T, R> getter, int min, int max) {
        R value = getter.apply(obj);
        int len = 0;
        if (value instanceof CharSequence cs) len = cs.length();
        else if (value instanceof Collection<?> c) len = c.size();
        else if (value instanceof Map<?, ?> m) len = m.size();
        else if (value instanceof Object[] a) len = a.length;

        if (len < min || len > max) {
            throwException(getter, ErrorCode.PARAM_LENGTH_LIMIT,
                    StrUtil.format("长度必须在 [{}, {}] 之间", min, max));
        }
    }

    // ========================================================================
    // 4. 格式校验 (正则)
    // ========================================================================

    /**
     * 正则匹配
     */
    public static <T> void match(T obj, SerFunction<T, String> getter, String regex, String errorMsg) {
        String value = getter.apply(obj);
        if (StrUtil.isNotEmpty(value) && !ReUtil.isMatch(regex, value)) {
            throwException(getter, ErrorCode.PARAM_FORMAT_ERROR, errorMsg);
        }
    }

    public static <T> void isEmail(T obj, SerFunction<T, String> getter) {
        String value = getter.apply(obj);
        if (StrUtil.isNotEmpty(value) && !Validator.isEmail(value)) {
            throwException(getter, ErrorCode.PARAM_FORMAT_ERROR, "格式不正确");
        }
    }

    public static <T> void isMobile(T obj, SerFunction<T, String> getter) {
        String value = getter.apply(obj);
        if (StrUtil.isNotEmpty(value) && !Validator.isMobile(value)) {
            throwException(getter, ErrorCode.PARAM_FORMAT_ERROR, "格式不正确");
        }
    }

    // ========================================================================
    // 私有助手
    // ========================================================================

    /**
     * 抛出异常 (只有出错时才调用，且才去解析Lambda字段名)
     */
    private static <T, R> void throwException(SerFunction<T, R> getter, ErrorCode code, String suffix) {
        // 利用 Hutool 的 LambdaUtil 解析方法名 (例如 getUsername -> username)
        String fieldName = LambdaUtil.getFieldName(getter);
        throw new BusinessException(code, fieldName + " " + suffix);
    }
}
