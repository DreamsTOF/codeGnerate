package com.dream.codegenerate.exception;

import com.dream.codegenerate.common.BaseResponse;
import com.dream.codegenerate.common.ResultUtils;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.exception.ExceptionMappingRegistry;


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
 * 全局异常处理器 (核心引擎 - 适配 JDK 25 / Spring Boot 4.0)
 * <p>
 * 整合了 ScopedValue 上下文、ExceptionMappingRegistry 自动映射。
 * 适配最新的 ErrorCode 分段标准 (1xxxx 客户端, 2xxxx 认证, 9xxxx 系统)。
 * </p>
 */
@RestControllerAdvice
@CustomLog
public class GlobalExceptionHandler {

    // =================================================================================
    // 1. 业务异常处理 (Service层手动抛出的逻辑错误)
    // =================================================================================

    /**
     * 捕获业务异常 (BusinessException)
     * <p>这是开发者主动抛出的预期内异常，通常包含用户友好的提示信息。</p>
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<?>> handleBusinessException(BusinessException e) {
        // 尝试反查 ErrorCode 枚举，用于日志分类
        ErrorCode errorCode = ErrorCode.getByCode(e.getCode());
        if (errorCode == ErrorCode.SYSTEM_ERROR) {
            // 如果反查失败(说明是旧的或临时的code)，归类为业务处理异常
            errorCode = ErrorCode.BUSINESS_ERROR;
        }

        // 记录日志 (业务异常通常是 info/warn 级别，但为了可溯源，这里使用 error 打印堆栈)
        logErrorWithContext(errorCode, e, e.getMessage());

        // 动态计算 HTTP 状态码
        HttpStatus status = determineHttpStatus(e.getCode());

        // 返回标准响应
        return ResponseEntity.status(status)
                .body(ResultUtils.error(e.getCode(), e.getMessage()));
    }

    // =================================================================================
    // 2. 参数校验与协议异常 (HTTP 400 级别)
    // =================================================================================

    /**
     * 处理 @Valid / @Validated 参数校验失败
     * <p>提取具体的字段错误信息，例如 "userName: 不能为空"</p>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();

        // 拼接详细错误信息
        String userMessage = ErrorCode.PARAMS_ERROR.getMessage();
        if (fieldError != null) {
            userMessage = String.format("%s: %s", fieldError.getField(), fieldError.getDefaultMessage());
        }

        // 记录日志 (归类为 10100 PARAMS_ERROR)
        logErrorWithContext(ErrorCode.PARAMS_ERROR, e, userMessage);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResultUtils.error(ErrorCode.PARAMS_ERROR, userMessage));
    }

    /**
     * 处理 JSON 解析失败 (例如 Body 格式不对，或者字段类型不匹配)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<?>> handleJsonParseException(HttpMessageNotReadableException e) {
        String userMessage = "请求Body格式错误或类型不匹配";

        // 归类为 10300 JSON_PARSE_ERROR
        logErrorWithContext(ErrorCode.JSON_PARSE_ERROR, e, userMessage);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResultUtils.error(ErrorCode.JSON_PARSE_ERROR, userMessage));
    }

    /**
     * 处理 404 资源未找到 (NoHandlerFoundException)
     * 需在配置中开启: spring.mvc.throw-exception-if-no-handler-found=true
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<BaseResponse<?>> handleNoHandlerFoundException(NoHandlerFoundException e) {
        logErrorWithContext(ErrorCode.RESOURCE_NOT_FOUND, e, e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResultUtils.error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // =================================================================================
    // 3. 智能自动映射兜底处理 (核心黑科技)
    // =================================================================================

    /**
     * 捕获所有未被上述方法捕获的异常 (Throwable)
     * <p>
     * 利用 ExceptionMappingRegistry 自动识别技术异常 (如 SQL 错误、Redis 错误、IO 错误)，
     * 并将其转换为对应的业务 ErrorCode。
     * </p>
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<BaseResponse<?>> handleAllExceptions(Throwable e) {

        // --- Step 1: 自动识别异常类型 (基于注册表) ---
        ErrorCode errorCode = ExceptionMappingRegistry.getErrorCode(e);

        // --- Step 2: 决定返回给前端的 Message ---
        String userMessage = errorCode.getMessage();

        // [特殊优化] 主键冲突时，尝试提取更多信息，或保持默认友好提示
        if (e instanceof DuplicateKeyException) {
            // ErrorCode.DUPLICATE_KEY (40100) 的默认提示是 "数据已存在(违反唯一约束)"
            // 可以保持默认，前端体验一致
        }

        // --- Step 3: 记录完整上下文日志 ---
        // 这里的日志是给后端看的，必须包含原始异常信息 (e.getMessage())
        logErrorWithContext(errorCode, e, e.getMessage());

        // --- Step 4: 决定 HTTP 状态码 ---
        HttpStatus status = determineHttpStatus(errorCode.getCode());

        // --- Step 5: 返回响应 ---
        return ResponseEntity.status(status)
                .body(ResultUtils.error(errorCode.getCode(), userMessage));
    }

    // =================================================================================
    // 4. 私有辅助方法
    // =================================================================================

    /**
     * 根据业务错误码决定 HTTP 状态码 (适配新版 ErrorCode 分段)
     * <p>
     * 规则:
     * - [10000 - 19999] 客户端错误 -> HTTP 400
     * - [20000 - 29999] 认证/安全  -> HTTP 401 / 403
     * - [30000 - 39999] 业务逻辑   -> HTTP 200 (业务层面的失败，通常返回200交给前端处理逻辑)
     * - [404xx]         资源未找到 -> HTTP 404
     * - [4xxxx, 5xxxx, 9xxxx] 系统/DB/中间件 -> HTTP 500
     * </p>
     */
    private HttpStatus determineHttpStatus(int code) {
        // 1xxxx: Client Error -> 400
        if (code >= 10000 && code < 20000) {
            return HttpStatus.BAD_REQUEST;
        }

        // 2xxxx: Auth -> 401 / 403
        if (code >= 20000 && code < 30000) {
            if (code == ErrorCode.UNAUTHORIZED.getCode() ||
                    code == ErrorCode.TOKEN_EXPIRED.getCode() ||
                    code == ErrorCode.TOKEN_INVALID.getCode()) {
                return HttpStatus.UNAUTHORIZED; // 401
            }
            if (code == ErrorCode.FORBIDDEN.getCode() ||
                    code == ErrorCode.ANONYMOUS_NOT_ALLOWED.getCode()) {
                return HttpStatus.FORBIDDEN; // 403
            }
            return HttpStatus.UNAUTHORIZED; // Default to 401
        }

        // 404xx: Resource Not Found -> 404
        if (code == ErrorCode.RESOURCE_NOT_FOUND.getCode() ||
                code == ErrorCode.DATA_NOT_FOUND.getCode()) {
            return HttpStatus.NOT_FOUND;
        }

        // 3xxxx: Business Logic -> 200 OK (前端解析 code 判断成功与否)
        if (code >= 30000 && code < 40000) {
            return HttpStatus.OK;
        }

        // 4xxxx (DB), 5xxxx (Middleware), 9xxxx (System) -> 500 Internal Server Error
        if (code >= 40000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // Default
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 统一日志记录器 (ScopedValue Aware)
     */
    private void logErrorWithContext(ErrorCode errorCode, Throwable e, String details) {
        OperationContext.OperationInfo context = OperationContext.get();
        String errorName = (errorCode != null) ? errorCode.name() : "UNKNOWN";
        int codeInt = (errorCode != null) ? errorCode.getCode() : -1;

        if (context != null) {
            long duration = System.currentTimeMillis() - context.getStartTime();
            log.error("""
                    
                    ============== ❌ {} [TraceId: {}] ==============
                    | Time      : {} (Duration: {}ms)
                    | Method    : {}
                    | Operator  : {}
                    | Params    : {}
                    | Exception : {}
                    | Code      : {}
                    | Details   : {}
                    ======================================================
                    """,
                    errorName,
                    context.getTraceId(), // ScopedValue 中的 TraceId
                    new Date(),
                    duration,
                    context.getMethodName(),
                    context.getOperatorId(),
                    Arrays.toString(context.getArgs()),
                    e.getClass().getSimpleName(),
                    codeInt,
                    details,
                    e);
        } else {
            log.error("❌ {} ({}): {} - {}", errorName, codeInt, details, e.getClass().getName(), e);
        }
    }
}
