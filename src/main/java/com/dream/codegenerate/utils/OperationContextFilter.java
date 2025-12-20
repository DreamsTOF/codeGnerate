package com.dream.codegenerate.utils;


import cn.hutool.core.util.IdUtil;
import com.dream.codegenerate.log.OperationContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.util.UUID;

/**
 * 操作上下文过滤器 (JDK 25 ScopedValue 版)
 * <p>
 * 在 JDK 25 中，我们不再使用 HandlerInterceptor + ThreadLocal。
 * 相反，我们使用 Filter 将整个请求链“包裹”在 ScopedValue 的作用域内。
 * </p>
 * 优势：
 * 1. 自动清理：ScopedValue 在 run() 块结束时自动失效，完全杜绝内存泄漏。
 * 2. 线程安全：ScopedValue 是不可变的，且在虚拟线程间传递性能极高。
 * 3. 强绑定：子任务（甚至异步子线程）可以无感继承该上下文。
 */
@Component
@Order(-100) // 确保在业务处理之前执行
public class OperationContextFilter implements Filter {

    private final RequestMappingHandlerMapping handlerMapping;

    public OperationContextFilter(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // 1. 提取元数据
        String traceId = httpRequest.getHeader("X-Trace-Id");
        if (traceId == null) traceId = IdUtil.fastSimpleUUID();

        // 2. 尝试识别业务描述 (通过 Spring HandlerMapping 预匹配)
        String module = "Unknown";
        String action = "Unknown";
        try {
            HandlerExecutionChain executionChain = handlerMapping.getHandler(httpRequest);
            if (executionChain != null && executionChain.getHandler() instanceof HandlerMethod handlerMethod) {
                Tag tag = handlerMethod.getBeanType().getAnnotation(Tag.class);
                if (tag != null) module = tag.name();

                Operation op = handlerMethod.getMethodAnnotation(Operation.class);
                if (op != null) action = op.summary();
            }
        } catch (Exception ignored) {}

        // 3. 构建 OperationInfo
        OperationContext.OperationInfo info = OperationContext.OperationInfo.builder()
                .traceId(traceId)
                .startTime(System.currentTimeMillis())
                .operatorId(parseOperatorId(httpRequest))
                .businessModule(module)
                .businessAction(action)
                .clientIp(getRemoteIP(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build();

        // 4. 【核心变化】使用 ScopedValue 绑定并执行
        // 整个 chain.doFilter 都在这个作用域内。
        // 一旦 run() 结束，ScopedValue 自动清理，不再需要任何 OperationContext.clear()！
        ScopedValue.where(OperationContext.getScopedValue(), info)
                   .run(() -> {
                       try {
                           if (response instanceof HttpServletResponse httpResponse) {
                               httpResponse.setHeader("X-Trace-Id", info.getTraceId());
                           }
                           chain.doFilter(request, response);
                       } catch (Exception e) {
                           throw new RuntimeException(e);
                       }
                   });
    }

    private Long parseOperatorId(HttpServletRequest request) {
        String val = request.getHeader("X-Operator-Id");
        try { return val != null ? Long.parseLong(val) : null; } catch (Exception e) { return null; }
    }

    private String getRemoteIP(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip.split(",")[0];
    }
}
