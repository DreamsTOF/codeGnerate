package com.dream.codegenerate.exception;


import cn.hutool.core.util.IdUtil;
import com.dream.codegenerate.log.OperationContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.CustomLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 操作日志切面 (公司生产版 - JDK 21 + TTL)
 * <p>
 * 核心逻辑：
 * 1. 兼容性：使用 TransmittableThreadLocal 确保在线程池异步任务中上下文不丢失。
 * 2. 自动化：通过反射抓取 OpenAPI 注解，将代码语义转化为业务语义。
 * 3. 安全性：在 finally 块中强制执行 clear()，彻底杜绝 ThreadLocal 内存泄漏。
 * </p>
 */
@Aspect
@Component
@CustomLog
public class OperationLogAspect {

    @Around("execution(* com.dream.codegenerate.controller.*.*(..))")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 1. 获取请求元数据
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 2. 解析方法与注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String module = "未知模块";
        String action = "未知动作";

        // 提取类注解 @Tag
        Tag tag = joinPoint.getTarget().getClass().getAnnotation(Tag.class);
        if (tag != null) module = tag.name();

        // 提取方法注解 @Operation
        Operation op = method.getAnnotation(Operation.class);
        if (op != null) action = op.summary();

        // 3. 构建上下文
        OperationContext.OperationInfo info = OperationContext.OperationInfo.builder()
                .traceId(request != null ? getTraceId(request) : IdUtil.fastSimpleUUID())
                .startTime(startTime)
                .methodName(signature.toShortString())
                .args(joinPoint.getArgs())
                .businessModule(module)
                .businessAction(action)
                .clientIp(request != null ? getRemoteIP(request) : "127.0.0.1")
                .userAgent(request != null ? request.getHeader("User-Agent") : "Internal")
                .build();

        try {
            // 4. 绑定上下文 (TTL 版 OperationContext)
            OperationContext.set(info);

            // 5. 执行目标方法
            return joinPoint.proceed();

        } finally {
            // 6. 核心：请求结束，必须清理 ThreadLocal 防止内存泄漏
            // 无论业务成功还是抛出异常，finally 都会执行
            OperationContext.clear();
        }
    }

    /**
     * 获取或生成链路追踪 ID
     */
    private String getTraceId(HttpServletRequest request) {
        String tid = request.getHeader("X-Trace-Id");
        return (tid != null && !tid.isEmpty()) ? tid : IdUtil.fastSimpleUUID();
    }

    /**
     * 获取客户端真实 IP (考虑代理情况)
     */
    private String getRemoteIP(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return (ip != null && ip.contains(",")) ? ip.split(",")[0] : ip;
    }
}
