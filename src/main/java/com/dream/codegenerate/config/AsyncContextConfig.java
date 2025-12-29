package com.dream.codegenerate.config;

import com.dream.codegenerate.log.autoAudit.ContextPropagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 全局异步执行器配置
 * <p>
 * 1. 自动传播 ScopedValue 上下文 (ContextPropagator)。
 * 2. 适配 Java 21 虚拟线程 (如果环境支持)。
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncContextConfig {

    @Bean("taskExecutor") // 指定 Bean 名称，防止冲突
    public ThreadPoolTaskExecutor taskExecutor() { // [关键修复] 返回具体类型而非接口
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1. [核心] 设置上下文传播器
        // 这一步确保异步线程能拿到当前登录人 (User) 和 TraceId
        executor.setTaskDecorator(new ContextPropagator());
        // 2. 尝试启用虚拟线程 (Java 21+)
        try {
            // 使用虚拟线程工厂，实现"线程随便开"的高并发能力
            executor.setThreadFactory(Thread.ofVirtual().name("virtual-audit-").factory());
            // 虚拟线程模式下，参数主要控制并发提交速率
            executor.setCorePoolSize(200);
            executor.setMaxPoolSize(1000);
            executor.setQueueCapacity(5000);
        } catch (Exception e) {
            // [降级] 如果不是 Java 21，回退到普通线程池
            executor.setThreadNamePrefix("Async-Audit-");
            executor.setCorePoolSize(10);
            executor.setMaxPoolSize(50);
            executor.setQueueCapacity(200);
        }
        // 3. 拒绝策略：由主线程执行 (防止日志丢失)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
