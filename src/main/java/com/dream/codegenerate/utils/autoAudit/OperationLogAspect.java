package com.dream.codegenerate.utils.autoAudit;


import cn.hutool.v7.core.data.id.IdUtil;
import com.dream.codegenerate.log.OperationContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.CustomLog;
import lombok.Getter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 操作日志切面 (JDK 25 ScopedValue 极限优化版)
 * <p>
 * 核心功能：
 * 1. 自动抓取：利用 AOP 拦截 Controller，自动提取 OpenAPI 注解描述。
 * 2. 身份追溯：通过 RequestContextHolder 抓取 IP、UA 和用户身份。
 * 3. 作用域绑定：将元数据绑定到 ScopedValue，供后续 SmartAuditUpdater 自动提取。
 * </p>
 */
@Aspect
@Component
@CustomLog
public class OperationLogAspect {

    @Around("execution(* com.dream.codegenerate.controller.*.*(..))")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 1. 获取当前请求对象
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 2. 解析方法元数据 (OpenAPI 注解)
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        String module = "Unknown";
        String action = "Unknown";

        // 获取类上的 @Tag
        Tag tag = joinPoint.getTarget().getClass().getAnnotation(Tag.class);
        if (tag != null) module = tag.name();

        // 获取方法上的 @Operation
        Operation op = method.getAnnotation(Operation.class);
        if (op != null) action = op.summary();

        // 3. 构建最先进的 OperationInfo
        OperationContext.OperationInfo info = OperationContext.OperationInfo.builder()
                .traceId(request != null ? getTraceId(request) : IdUtil.fastSimpleUUID())
                .startTime(startTime)
                .methodName(signature.toShortString())
                .args(joinPoint.getArgs())
                .businessModule(module)
                .businessAction(action)
                .clientIp(request != null ? getRemoteIP(request) : "127.0.0.1")
                .userAgent(request != null ? request.getHeader("User-Agent") : "Internal")
                // .operatorId(...) // 这里可以对接你的鉴权工具类，如 StpUtil.getLoginIdAsLong()
                .build();

        // 4. 使用 ScopedValue 开启作用域并执行
        try {
            return ScopedValue.where(OperationContext.getScopedValue(), info)
                    .call(() -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            // 包装异常以在 Lambda 外精准还原
                            throw new ThrowableWrapper(e);
                        }
                    });
        } catch (ThrowableWrapper e) {
            throw e.getOriginal();
        }
    }

    private String getTraceId(HttpServletRequest request) {
        String tid = request.getHeader("X-Trace-Id");
        return tid != null ? tid : IdUtil.fastSimpleUUID();
    }

    private String getRemoteIP(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0] : ip;
    }

    @Getter
    private static class ThrowableWrapper extends RuntimeException {
        private final Throwable original;
        public ThrowableWrapper(Throwable original) {
            super(null, original, false, false);
            this.original = original;
        }
    }
}
