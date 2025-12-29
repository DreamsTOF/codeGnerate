package com.dream.codegenerate.log.autoAudit;

import com.alibaba.ttl.TtlRunnable;
import com.dream.codegenerate.log.OperationContext;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.task.TaskDecorator;
import java.util.concurrent.Callable;

/**
 * 异步上下文传播工具 (JDK 25 适配版)
 * <p>
 * 修复：ScopedValue.CallableOp 与 java.util.concurrent.Callable 的类型冲突。
 * 核心逻辑：在提交任务前捕获 ScopedValue，在子线程执行时使用 call() 或 run() 重新绑定。
 * </p>
 */
public class ContextPropagator implements TaskDecorator {

    /**
     * 为 Spring ThreadPoolTaskExecutor 提供的装饰器
     * 自动处理 @Async 任务的上下文传递
     */
    @NotNull
    @Override
    public Runnable decorate(@NotNull Runnable runnable) {
        // 1. 捕获当前父线程绑定的上下文
        OperationContext.OperationInfo info = OperationContext.get();

        return () -> {
            // 2. 在子线程中重新绑定
            // 注意：此处使用 run 接受 Runnable 任务
            ScopedValue.where(OperationContext.getScopedValue(), info).run(runnable);
        };
    }

    /**
     * 手动包装 Runnable
     */
    public static Runnable wrap(Runnable runnable) {
        OperationContext.OperationInfo info = OperationContext.get();
        return () -> ScopedValue.where(OperationContext.getScopedValue(), info).run(runnable);
    }

    /**
     * 手动包装 Callable (修复编译错误的关键)
     * <p>
     * 错误原因：ScopedValue.where().call() 接受的是 ScopedValue.CallableOp
     * 解决：使用 Lambda 表达式 callable::call 显式转换为目标函数式接口
     * </p>
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        OperationContext.OperationInfo info = OperationContext.get();
        // 使用 Lambda 表达式 callable::call 来适配 ScopedValue.CallableOp
        return () -> ScopedValue.where(OperationContext.getScopedValue(), info).call(callable::call);
    }


//    @NotNull
//    @Override
//    public Runnable decorate(@NotNull Runnable runnable) {
//        // [修正] 直接使用 TTL 的包装器，不再调用 getScopedValue()
//        return TtlRunnable.get(runnable);
//    }
}
