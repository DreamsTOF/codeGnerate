package com.dream.codegenerate.exception;

import com.dream.codegenerate.common.BaseResponse;
import com.dream.codegenerate.common.ResultUtils;
import com.dream.codegenerate.log.OperationContext;
import lombok.CustomLog;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Arrays;
import java.util.Date;

/**
 * 全局异常处理器 (优化版)
 * <p>
 * 改进点：
 * 1. 日志分级：业务异常(Warn) vs 系统异常(Error)。
 * 2. 上下文集成：完美复用 OperationLogAspect 绑定的 ScopedValue。
 * </p>
 */
@RestControllerAdvice
@CustomLog
public class GlobalExceptionHandler {

    // =================================================================================
    // 1. 业务异常处理 (预期内的逻辑错误 -> WARN)
    // =================================================================================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<?>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = ErrorCode.getByCode(e.getCode());
        if (errorCode == ErrorCode.SYSTEM_ERROR) {
            errorCode = ErrorCode.BUSINESS_ERROR;
        }

        // [优化] 业务异常通常是用户操作错误（如密码不对、余额不足），
        // 不需要打印堆栈信息，使用 WARN 级别记录关键信息即可，保持日志清爽。
        logWarnWithContext(errorCode, e.getMessage());

        HttpStatus status = determineHttpStatus(e.getCode());
        return ResponseEntity.status(status)
                .body(ResultUtils.error(e.getCode(), e.getMessage()));
    }

    // =================================================================================
    // 2. 参数与协议异常 (客户端错误 -> WARN)
    // =================================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();
        String userMessage = ErrorCode.PARAMS_ERROR.getMessage();
        if (fieldError != null) {
            userMessage = String.format("%s: %s", fieldError.getField(), fieldError.getDefaultMessage());
        }

        // 参数错误也是客户端问题，使用 WARN
        logWarnWithContext(ErrorCode.PARAMS_ERROR, userMessage);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResultUtils.error(ErrorCode.PARAMS_ERROR, userMessage));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<?>> handleJsonParseException(HttpMessageNotReadableException e) {
        String userMessage = "请求Body格式错误或类型不匹配";
        logWarnWithContext(ErrorCode.JSON_PARSE_ERROR, userMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResultUtils.error(ErrorCode.JSON_PARSE_ERROR, userMessage));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        logWarnWithContext(ErrorCode.RESOURCE_NOT_FOUND, e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResultUtils.error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // =================================================================================
    // 3. 系统兜底异常 (意料之外的错误 -> ERROR)
    // =================================================================================

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<BaseResponse<?>> handleAllExceptions(Throwable e) {
        ErrorCode errorCode = ExceptionMappingRegistry.getErrorCode(e);
        String userMessage = errorCode.getMessage();

        if (e instanceof DuplicateKeyException) {
            // 数据库唯一键冲突，属于隐式的业务规则违反，也可以考虑降级为 WARN
            // 但为了排查是哪个键冲突，这里暂时保留 ERROR 或视情况调整
        }

        // [重点] 系统异常（空指针、DB连接失败等）必须打印完整堆栈，以便排查 Bug
        logErrorWithContext(errorCode, e, e.getMessage());

        HttpStatus status = determineHttpStatus(errorCode.getCode());
        return ResponseEntity.status(status)
                .body(ResultUtils.error(errorCode.getCode(), userMessage));
    }

    // =================================================================================
    // 4. 私有辅助方法
    // =================================================================================

    private HttpStatus determineHttpStatus(int code) {
        if (code >= 10000 && code < 20000) return HttpStatus.BAD_REQUEST;
        if (code >= 20000 && code < 30000) {
            if (code == ErrorCode.UNAUTHORIZED.getCode()) return HttpStatus.UNAUTHORIZED;
            if (code == ErrorCode.FORBIDDEN.getCode()) return HttpStatus.FORBIDDEN;
            return HttpStatus.UNAUTHORIZED;
        }
        if (code == ErrorCode.RESOURCE_NOT_FOUND.getCode()) return HttpStatus.NOT_FOUND;
        if (code >= 30000 && code < 40000) return HttpStatus.BAD_REQUEST;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 记录 WARN 日志 (无堆栈，轻量级)
     */
    private void logWarnWithContext(ErrorCode errorCode, String details) {
        // OperationContext.get() 永远不会返回 null (有默认兜底)，直接使用即可
        OperationContext.OperationInfo context = OperationContext.get();

        String errorName = (errorCode != null) ? errorCode.name() : "UNKNOWN";
        int codeInt = (errorCode != null) ? errorCode.getCode() : -1;

        // 格式对齐：[WARN ] TraceId | Module | Error | Msg
        log.warn("⚠️ [{}] | {} | {} | Code:{} | {}",
                context.getBusinessModule(),
                context.getTraceId(),
                errorName,
                codeInt,
                details);
    }

    /**
     * 记录 ERROR 日志 (带完整堆栈，用于排查 Bug)
     */
    private void logErrorWithContext(ErrorCode errorCode, Throwable e, String details) {
        OperationContext.OperationInfo context = OperationContext.get();
        String errorName = (errorCode != null) ? errorCode.name() : "UNKNOWN";
        int codeInt = (errorCode != null) ? errorCode.getCode() : -1;
        long duration = System.currentTimeMillis() - context.getStartTime();

        log.error("""
                
                ============== ❌ SYSTEM ERROR [TraceId: {}] ==============
                | Time      : {} (Duration: {}ms)
                | Method    : {}
                | Operator  : {} ({})
                | Params    : {}
                | Exception : {}
                | ErrorCode : {} ({})
                | Details   : {}
                ===========================================================
                """,
                context.getTraceId(),
                new Date(),
                duration,
                context.getMethodName(),
                context.getOperatorName(), context.getOperatorId(), // 打印出是谁操作的
                Arrays.toString(context.getArgs()),
                e.getClass().getName(),
                codeInt, errorName,
                details,
                e); // 传入 e 打印堆栈
    }
}
