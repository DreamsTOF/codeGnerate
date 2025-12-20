package com.dream.codegenerate.utils;


import com.dream.codegenerate.exception.ErrorCode;
import com.mybatisflex.core.mask.Masks;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.mybatisflex.core.util.UpdateEntity;
import lombok.CustomLog;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * OptimisticLocker - 通用乐观锁与并发控制工具
 * <p>
 * 解决痛点：
 * 1. 自动化“读取-修改-回写”的版本冲突重试逻辑。
 * 2. 深度适配 MyBatis-Flex，支持最小化更新。
 * 3. 统一并发冲突的异常处理与重试策略。
 * </p>
 *
 * @param <E> 实体类型
 */
@CustomLog
public class OptimisticLocker<E> {

    private final Serializable id;
    private final IService<E> service;
    private int maxRetryTimes = 0;
    private final String className;

    private OptimisticLocker(Serializable id, IService<E> service) {
        this.id = id;
        this.service = service;
        this.className = service.getClass().getSimpleName().replace("ServiceImpl", "");
    }

    /**
     * 初始化锁定器
     * @param id 实体ID
     * @param service 实体的 IService 实例
     */
    public static <E> OptimisticLocker<E> of(Serializable id, IService<E> service) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAM_IS_NULL, "ID不可为空");
        return new OptimisticLocker<>(id, service);
    }

    /**
     * 设置重试次数
     * @param times 冲突后的重试次数 (默认0)
     */
    public OptimisticLocker<E> retry(int times) {
        this.maxRetryTimes = Math.max(0, times);
        return this;
    }

    /**
     * 执行业务更新逻辑
     * <p>
     * 逻辑流：
     * 1. 根据 ID 查出最新实体（含版本号）。
     * 2. 执行传入的 Lambda 修改实体属性。
     * 3. 调用 updateById，MyBatis-Flex 会自动处理 WHERE version = ? 逻辑。
     * 4. 若影响行数为 0，触发重试或抛出并发异常。
     * </p>
     *
     * @param updateAction 定义业务修改逻辑的 Lambda
     */
    public void execute(Consumer<E> updateAction) {
        int attempt = 0;
        while (attempt <= maxRetryTimes) {
            // 1. 获取最新数据 (开启强制从主库读取或清除缓存，视具体环境而定)
            E entity = service.getById(id);
            ThrowUtils.throwIf(entity == null, ErrorCode.DATA_NOT_FOUND, className + " 数据不存在");

            // 2. 执行业务逻辑
            updateAction.accept(entity);

            // 3. 尝试更新
            // MyBatis-Flex 会根据实体的 @Column(version = true) 自动生成乐观锁 SQL
            boolean success = service.updateById(entity);

            if (success) {
                if (attempt > 0) {
                    log.info("OptimisticLocker - [{} ID:{}]: 冲突重试成功, 耗费轮次:{}", className, id, attempt);
                }
                return;
            }

            // 4. 冲突处理
            attempt++;
            if (attempt <= maxRetryTimes) {
                log.warn("OptimisticLocker - [{} ID:{}]: 检测到并发冲突，准备第 {} 次重试", className, id, attempt);
                // 这里可以根据需要添加短暂的随机 Backoff 睡眠，减少竞争
                backoff(attempt);
            } else {
                log.error("OptimisticLocker - [{} ID:{}]: 并发冲突重试耗尽", className, id);
                ThrowUtils.throwIf(true, ErrorCode.OPTIMISTIC_LOCK_ERROR);
            }
        }
    }

    /**
     * 简单的退避策略，随着重试次数增加睡眠时间
     */
    private void backoff(int attempt) {
        try {
            // 基础 50ms * 轮次，最大程度错开并发
            Thread.sleep(attempt * 50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
