package com.dream.codegenerate.log.autoAudit;

import cn.hutool.v7.core.data.id.IdUtil;
import cn.hutool.v7.json.JSONUtil;
import com.dream.codegenerate.log.OperationContext;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.CustomLog;
import lombok.Getter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志切面 (OperationLogAspect) - 顶层入口
 * <p>
 * 职责：
 * 1. 初始化 ScopedValue 上下文 (TraceId, User, IP)。
 * 2. 兜底打印：记录"非审计类操作"(如登录) 或 "失败的操作"。
 * </p>
 */
@Aspect
@Component
@CustomLog
@Order(1) // [关键] 必须是 Order(1)，保证在 AutoAuditAspect 之前执行，建立 Scope
public class OperationLogAspect {

    @Value("${spring.application.name:Unknown-App}")
    private String applicationName;

    // [关键] 使用 Lazy 防止与 AuthInterceptor 或其他 Bean 循环依赖
    @Lazy
    @Resource
    private UserService userService;

    @Around("execution(* com.dream.codegenerate.controller.*.*(..))")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 1. 环境准备
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 2. 解析元数据
        String module = resolveModule(joinPoint);
        String action = resolveAction(method);

        // 3. 获取用户信息 (静默模式，防止未登录接口报错)
        Long opId = null;
        String opName = "Guest";
        try {
            if (request != null) {
                User loginUser = userService.getLoginUser(request);
                if (loginUser != null) {
                    opId = loginUser.getId();
                    opName = loginUser.getUserName();
                }
            }
        } catch (Exception ignored) {
            // 忽略未登录异常
        }

        // 4. 构建上下文 (Context)
        OperationContext.OperationInfo info = OperationContext.OperationInfo.builder()
                .traceId(request != null ? request.getHeader("X-Trace-Id") : IdUtil.fastSimpleUUID())
                .startTime(startTime)
                .methodName(signature.toShortString())
                .args(joinPoint.getArgs())
                .businessModule(module)
                .businessAction(action)
                .operatorId(opId)
                .operatorName(opName)
                .clientIp(getRemoteIP(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : "Internal")
                .build();

        // 5. 判断是否需要自动审计
        boolean hasAuditLog = method.isAnnotationPresent(AuditLog.class);
        //ttl 模式
//        OperationContext.set(info);
        //ttl 模式
        // 6. 绑定 ScopedValue 并执行业务
        boolean success = true;
        String errorMsg = null;
        try {
            // 使用 ScopedValue 绑定上下文，供下游 (AutoAuditAspect / Service / Mapper) 使用
            return ScopedValue.where(OperationContext.getScopedValue(), info).call(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    throw new ThrowableWrapper(e);
                }
            });
            //ttl 模式
//            return joinPoint.proceed();
            //ttl 模式
        } catch (ThrowableWrapper e) {
            success = false;
            errorMsg = e.getOriginal().getMessage();
            throw e.getOriginal(); // 抛出原始异常给 GlobalExceptionHandler
        } catch (Exception e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            // 7. 智能日志策略
            // 策略 A: 如果业务失败 -> 必须打印 ACCESS_LOG (因为事务回滚，审计日志不会记录)
            // 策略 B: 如果没有 @AuditLog -> 必须打印 ACCESS_LOG (如登录、查询)
            // 策略 C: 成功且有 @AuditLog -> 不打印 (交给 AutoAuditAspect 打印详细变更)
            if (!success || !hasAuditLog) {
                printAccessLog(info, System.currentTimeMillis() - startTime, success, errorMsg);
            }
            // [必须] 清理 ThreadLocal 防止内存泄漏和数据污染
            //ttl 模式
//            OperationContext.clear();
            //ttl 模式
        }
    }

    private void printAccessLog(OperationContext.OperationInfo info, long cost, boolean success, String errorMsg) {
        Map<String, Object> logMap = new LinkedHashMap<>();
        logMap.put("logType", "ACCESS_LOG");
        logMap.put("traceId", info.getTraceId());
        logMap.put("module", info.getBusinessModule());
        logMap.put("action", info.getBusinessAction());
        logMap.put("operator", info.getOperatorName());
        logMap.put("ip", info.getClientIp());
        logMap.put("costTime", cost);
        logMap.put("status", success ? "SUCCESS" : "FAIL");
        if (!success) {
            logMap.put("error", errorMsg);
        }
        log.info("ACCESS_LOG: {}", JSONUtil.toJsonStr(logMap));
    }

    // --- Helpers ---

    private String resolveModule(ProceedingJoinPoint joinPoint) {
        Tag tag = joinPoint.getTarget().getClass().getAnnotation(Tag.class);
        return (tag != null && !tag.name().isEmpty()) ? tag.name() : applicationName;
    }

    private String resolveAction(Method method) {
        Operation op = method.getAnnotation(Operation.class);
        return (op != null) ? op.summary() : method.getName();
    }

    private String getRemoteIP(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
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
