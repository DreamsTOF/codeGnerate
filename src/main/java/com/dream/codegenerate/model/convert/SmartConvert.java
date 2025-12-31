package com.dream.codegenerate.model.convert;

/**
 * 智能转换接口
 * <p>
 * 通过统一的入口方法，消除 SmartQuery 在运行时的反射调用开销。
 * </p>
 * @param <R> 目标 VO 类型
 */
public interface SmartConvert<R> {

    /**
     * 通用转换入口
     * 实现类需根据 args 的长度或类型，分发给具体的 MapStruct 方法
     * * @param args 组装好的实体参数 (如 [App, User])
     * @return 转换后的 VO
     */
    R toVO(Object... args);
}
