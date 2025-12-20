package com.dream.codegenerate.log.core;



import com.dream.codegenerate.log.OperationContext;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步日志写入器 (Log Writer) - 虚拟线程版
 * 职责：
 * 1. 管理一个阻塞队列。
 * 2. 运行在虚拟线程上，以极低的资源消耗处理日志 I/O。
 * 3. 实现了智能背压策略和优雅停机。
 */
public class LogWriter implements Runnable {

    // 队列容量：8192 是一个经验值，既能缓冲突发流量，又不至于占用过多堆内存
    private static final int QUEUE_CAPACITY = 8192;
    private final BlockingQueue<LogEvent> eventQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    // 控制写入线程的生命周期
    private final AtomicBoolean running = new AtomicBoolean(true);

    // 日期格式化器
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    // 单例实例
    private static final LogWriter SINGLETON = new LogWriter();

    /**
     * 静态初始化：启动虚拟线程和注册 Shutdown Hook
     */
    static {
        // [建议1] 虚拟线程化
        // 虚拟线程默认是 Daemon 线程，JVM 退出时会自动停止，所以必须配合 Shutdown Hook 使用
        Thread.ofVirtual()
                .name("Log-Virtual-Writer")
                .start(SINGLETON);

        // [建议2] 优雅停机 (Graceful Shutdown)
        // 当 JVM 收到 kill 信号时，强制将队列中剩余的日志刷盘
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[LogSystem] JVM is shutting down. Flushing log queue...");
            SINGLETON.running.set(false); // 停止接收新日志
            SINGLETON.flushQueue();       // 刷出剩余日志
            System.out.println("[LogSystem] Shutdown complete.");
        }, "Log-Shutdown-Hook"));
    }

    private LogWriter() {}

    /**
     * [建议3] 智能背压投递策略
     * 根据日志重要程度决定：丢弃还是阻塞
     */
    public static void enqueue(LogEvent event) {
        // 判断日志级别重要性 (这里假设 LogLevel 枚举顺序反映了重要性)
        boolean isCritical = event.getLevel().compareTo(LogLevel.ERROR) >= 0;

        if (isCritical) {
            // 策略 A: 关键日志 (ERROR/FATAL) -> 必须写入，不可丢失
            // 如果队列满了，让业务线程阻塞等待 (Put)，确保存活
            try {
                SINGLETON.eventQueue.put(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 如果被中断，降级为控制台直接输出，确保不丢
                System.err.println("❌ Interrupted while queuing CRITICAL log: " + event.getMessage());
            }
        } else {
            // 策略 B: 普通日志 (INFO/DEBUG) -> 性能优先
            // 如果队列满了，直接丢弃 (Drop)，保护业务线程不卡顿
            if (!SINGLETON.eventQueue.offer(event)) {
                // 仅在控制台打印一条简短的丢弃提示
                // System.err.println("⚠️ Log queue full, dropping " + event.getLevel() + " log.");
            }
        }
    }

    @Override
    public void run() {
        while (running.get() || !eventQueue.isEmpty()) {
            try {
                // 虚拟线程挂起等待，不消耗 CPU
                LogEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    processEvent(event);
                }
            } catch (InterruptedException e) {
                // 恢复中断状态
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                // 兜底异常捕获，防止写入线程崩溃
                System.err.println("Fatal error in Log Writer virtual thread: " + t.getMessage());
            }
        }
    }

    /**
     * 刷新队列 (通常在停机时调用)
     */
    private void flushQueue() {
        LogEvent event;
        while ((event = eventQueue.poll()) != null) {
            try {
                processEvent(event);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    /**
     * 执行真正的日志 I/O
     */
    private void processEvent(LogEvent event) {
        OperationContext.OperationInfo context = event.getContext();
        String message = event.getMessage();
        Throwable t = event.getThrowable();
        LogLevel level = event.getLevel();

        StringBuilder sb = new StringBuilder(512);

        // 简单的控制台高亮 (可选)
        String color = level.compareTo(LogLevel.ERROR) >= 0 ? "\u001B[31m" : "\u001B[32m";
        String reset = "\u001B[0m";

        if (context != null) {
            // 结构化输出
            long duration = event.getTimestamp() - context.getStartTime();

            sb.append(color).append("\n=== [").append(level.name()).append("] ");
            sb.append("[TraceId: ").append(context.getTraceId()).append("] ===\n");

            sb.append("| Time     : ").append(DATE_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()))).append("\n");
            sb.append("| Logger   : ").append(event.getLoggerName()).append("\n");
            sb.append("| Method   : ").append(context.getMethodName()).append("\n");
            sb.append("| Cost     : ").append(duration).append("ms\n");
            sb.append("| Message  : ").append(message).append("\n");

            if (t != null) {
                sb.append("| Ex       : ").append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
            }
            sb.append("========================================\n").append(reset);
        } else {
            // 简单输出
            sb.append(color)
                    .append("[").append(DATE_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()))).append("] ")
                    .append("[").append(Thread.currentThread().getName()).append("] ") // 这里会显示 VirtualThread
                    .append(level.name()).append(" ")
                    .append(event.getLoggerName()).append(" - ")
                    .append(message)
                    .append(reset);

            if (t != null) {
                sb.append("\n").append(t.toString());
            }
        }

        // 最终写入 (System.out 在 JDK 21+ 的虚拟线程下已优化，不再是由于锁竞争导致的严重瓶颈)
        // 生产环境建议替换为 FileOutputStream 或 Socket 输出
        System.out.println(sb.toString());
    }
}
