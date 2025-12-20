package com.dream.codegenerate.utils;

import java.io.Serializable;

/**
 * 自定义双参数无返回值函数式接口，支持 Lambda 解析
 */
@FunctionalInterface
public interface VoidFunc2<T, P> extends Serializable {
    void apply(T target, P value) throws Exception;
}
