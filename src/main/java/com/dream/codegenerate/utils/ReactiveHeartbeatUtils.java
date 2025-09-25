package com.dream.codegenerate.utils;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Objects;

/**
 * 一个为响应式流 (Reactive Stream) 提供 Server-Sent Events (SSE) 心跳机制的工具类。
 * <p>
 * 【版本 2.0 - 已修复无限流问题】
 * 此版本修复了心跳流永不结束从而导致下游操作（如 concatWith）无法执行的问题。
 * 心跳现在会在主数据流结束后自动停止。
 *
 * @version 2.0
 * @since Java 21
 */
public final class ReactiveHeartbeatUtils {

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final String HEARTBEAT_COMMENT = "keep-alive";

    private ReactiveHeartbeatUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 为给定的 SSE Flux 流添加心跳机制，使用默认的 30 秒间隔。
     *
     * @param sourceStream 原始的数据流，它必须是一个会正常结束的有限流。
     * @param <T>          流中数据的类型
     * @return 一个包含了心跳事件、并能与源流一同正常结束的新 Flux 流。
     */
    public static <T> Flux<ServerSentEvent<T>> withHeartbeat(Flux<ServerSentEvent<T>> sourceStream) {
        return withHeartbeat(sourceStream, DEFAULT_HEARTBEAT_INTERVAL);
    }

    /**
     * 为给定的 SSE Flux 流添加心跳机制，使用自定义的间隔。
     * <p>
     * 核心修复逻辑：
     * 1. 使用 .publish() 将 sourceStream 转换为一个共享的 "热" Flux (ConnectableFlux)。
     * 这允许多个内部订阅者（一个是向下游传递数据，另一个是作为心跳的停止信号）
     * 共享来自 sourceStream 的同一个事件序列。
     * 2. 创建一个无限的心跳流 heartbeatTicks。
     * 3. 使用 .takeUntilOther(sharedSource.then()) 来控制心跳流。
     * - sharedSource.then() 会创建一个 Mono<Void>，它在 sharedSource 成功完成 (onComplete) 时发出完成信号。
     * - takeUntilOther 会订阅这个 Mono，一旦收到信号，它就会立即停止心跳流。
     * 4. 最后，使用 Flux.merge 合并原始数据流和现在可以被终止的心跳流。
     *
     * @param sourceStream      原始的数据流
     * @param heartbeatInterval 心跳发送的间隔时间
     * @param <T>               流中数据的类型
     * @return 一个包含了心跳事件、并能与源流一同正常结束的新 Flux 流。
     */
    public static <T> Flux<ServerSentEvent<T>> withHeartbeat(
            Flux<ServerSentEvent<T>> sourceStream,
            Duration heartbeatInterval) {

        Objects.requireNonNull(sourceStream, "The source stream must not be null.");
        Objects.requireNonNull(heartbeatInterval, "The heartbeat interval must not be null.");

        return sourceStream.publish(sharedSource -> {
            // 创建一个无限的、周期性的心跳流
            Flux<ServerSentEvent<T>> heartbeatTicks = Flux.interval(heartbeatInterval)
                    .map(tick -> createHeartbeatEvent());

            // 创建一个在源数据流结束后就会停止的心跳流
            Flux<ServerSentEvent<T>> stoppableHeartbeatStream = heartbeatTicks
                    .takeUntilOther(sharedSource.then());

            // 合并源数据流和可停止的心跳流
            return Flux.merge(sharedSource, stoppableHeartbeatStream);
        });
    }

    private static <T> ServerSentEvent<T> createHeartbeatEvent() {
        return ServerSentEvent.<T>builder()
                .comment(HEARTBEAT_COMMENT)
                .build();
    }
}

