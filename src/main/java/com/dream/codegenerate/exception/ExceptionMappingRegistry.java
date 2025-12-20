package com.dream.codegenerate.exception;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// =================================================================================
// 1. JDK Standard (Runtime, IO, Reflect, Net, SQL) - 40+ Mappings
// =================================================================================
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.*;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.*;

// JDK 21+ Specific
import java.lang.MatchException;
import java.lang.WrongThreadException;
//import java.util.concurrent.StructureViolationException;

// =================================================================================
// 2. Jackson JSON (Data Binding) - 10+ Mappings
// =================================================================================
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.*;

// =================================================================================
// 3. Spring Framework Core & Beans & Context - 15+ Mappings
// =================================================================================
import org.springframework.beans.*;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.util.InvalidMimeTypeException;

// =================================================================================
// 4. Spring Web & MVC - 25+ Mappings
// =================================================================================
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.*;
import org.springframework.web.client.*;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

// =================================================================================
// 5. Spring Security 6.0+ & OAuth2 - 15+ Mappings
// =================================================================================
//import org.springframework.security.access.AccessDeniedException;
//import org.springframework.security.access.AuthorizationServiceException;
//import org.springframework.security.authentication.*;
//import org.springframework.security.authorization.AuthorizationDeniedException;
//import org.springframework.security.core.AuthenticationException;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
//import org.springframework.security.oauth2.jwt.JwtException;
//import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

// =================================================================================
// 6. Spring Data (JDBC, Transaction, Redis, Mongo, ES) - 25+ Mappings
// =================================================================================
import org.springframework.dao.*;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.*;

// Redis
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.serializer.SerializationException;

// =================================================================================
// 7. Third Party (Validation, Resilience4j) - 10+ Mappings
// =================================================================================
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
//import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
//import io.github.resilience4j.ratelimiter.RequestNotPermitted;
//import io.github.resilience4j.bulkhead.BulkheadFullException;


import javax.security.auth.login.AccountExpiredException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终极异常映射注册表 (Total Mappings: >130)
 * 适配 JDK 25 + Spring Boot 4.0
 * * <p>策略：静态注册 + 运行时缓存 + 智能拆包</p>
 */
public class ExceptionMappingRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExceptionMappingRegistry.class);

    // 初始容量设为 512，避免扩容开销
    private static final Map<Class<? extends Throwable>, ErrorCode> EXCEPTION_MAPPINGS = new HashMap<>(512);
    // 运行时缓存 (High Performance)
    private static final Map<Class<? extends Throwable>, ErrorCode> CACHED_ERROR_CODES = new ConcurrentHashMap<>(512);

    static {
        // ========================================================================
        // [Group 1] JDK Runtime & Core
        // ========================================================================
        register(NullPointerException.class, ErrorCode.NULL_POINTER);
        register(ClassCastException.class, ErrorCode.CAST_ERROR);
        register(IndexOutOfBoundsException.class, ErrorCode.INDEX_OUT_OF_BOUNDS);
        register(ArrayIndexOutOfBoundsException.class, ErrorCode.INDEX_OUT_OF_BOUNDS);
        register(StringIndexOutOfBoundsException.class, ErrorCode.INDEX_OUT_OF_BOUNDS);
        register(ArrayStoreException.class, ErrorCode.PARAM_TYPE_MISMATCH);
        register(ArithmeticException.class, ErrorCode.ARITHMETIC_ERROR);
        register(IllegalArgumentException.class, ErrorCode.PARAM_VALUE_INVALID);
        register(IllegalStateException.class, ErrorCode.STATUS_ERROR);
        register(NumberFormatException.class, ErrorCode.PARAM_FORMAT_ERROR);
        register(DateTimeParseException.class, ErrorCode.PARAM_FORMAT_ERROR);
        register(UnsupportedOperationException.class, ErrorCode.HTTP_METHOD_NOT_SUPPORTED);
        register(SecurityException.class, ErrorCode.FORBIDDEN);
        register(CloneNotSupportedException.class, ErrorCode.CLONE_ERROR);
        register(NoSuchElementException.class, ErrorCode.RESOURCE_NOT_FOUND);
        register(ConcurrentModificationException.class, ErrorCode.CONCURRENT_ERROR);

        // Reflection
        register(ReflectiveOperationException.class, ErrorCode.SYSTEM_ERROR);
        register(ClassNotFoundException.class, ErrorCode.CLASS_NOT_FOUND);
        register(NoClassDefFoundError.class, ErrorCode.CLASS_NOT_FOUND);
        register(NoSuchMethodException.class, ErrorCode.METHOD_NOT_FOUND);
        register(NoSuchFieldException.class, ErrorCode.FIELD_NOT_FOUND);
        register(InstantiationException.class, ErrorCode.INSTANTIATION_ERROR);
        register(InvocationTargetException.class, ErrorCode.SYSTEM_ERROR);

        // JDK 21+ New Features
        register(MatchException.class, ErrorCode.MATCH_ERROR);
        register(WrongThreadException.class, ErrorCode.VIRTUAL_THREAD_ERROR);
//        register(StructureViolationException.class, ErrorCode.STRUCTURED_CONCURRENCY_ERROR);

        // ========================================================================
        // [Group 2] IO & Network
        // ========================================================================
        register(IOException.class, ErrorCode.IO_ERROR);
        register(FileNotFoundException.class, ErrorCode.FILE_NOT_FOUND);
        register(AccessDeniedException.class, ErrorCode.FORBIDDEN); // java.nio.file
        register(FileSystemException.class, ErrorCode.IO_ERROR);
        register(EOFException.class, ErrorCode.IO_ERROR);
        register(UnsupportedCharsetException.class, ErrorCode.PARAM_FORMAT_ERROR);

        // Net
        register(SocketTimeoutException.class, ErrorCode.SOCKET_TIMEOUT);
        register(ConnectException.class, ErrorCode.RPC_CONNECTION_REFUSED);
        register(BindException.class, ErrorCode.NETWORK_ERROR); // Port in use
        register(UnknownHostException.class, ErrorCode.UNKNOWN_HOST);
        register(ProtocolException.class, ErrorCode.NETWORK_ERROR);
        register(MalformedURLException.class, ErrorCode.PARAM_FORMAT_ERROR);
        register(InterruptedIOException.class, ErrorCode.THREAD_INTERRUPTED);

        // ========================================================================
        // [Group 3] Concurrency
        // ========================================================================
        register(RejectedExecutionException.class, ErrorCode.THREAD_POOL_EXHAUSTED);
        register(TimeoutException.class, ErrorCode.EXECUTION_TIMEOUT);
        register(InterruptedException.class, ErrorCode.THREAD_INTERRUPTED);
        register(CancellationException.class, ErrorCode.OPERATION_FAILED);
        register(CompletionException.class, ErrorCode.FUTURE_ERROR);
        register(ExecutionException.class, ErrorCode.FUTURE_ERROR);
        register(BrokenBarrierException.class, ErrorCode.CONCURRENT_ERROR);

        // ========================================================================
        // [Group 4] Jackson JSON (Detailed)
        // ========================================================================
        register(JsonProcessingException.class, ErrorCode.JSON_PARSE_ERROR);
        register(JsonParseException.class, ErrorCode.JSON_PARSE_ERROR);
        register(JsonEOFException.class, ErrorCode.JSON_PARSE_ERROR);

        // Mapping
        register(JsonMappingException.class, ErrorCode.JSON_MAPPING_ERROR);
        register(InvalidFormatException.class, ErrorCode.JSON_FORMAT_INVALID);
        register(MismatchedInputException.class, ErrorCode.JSON_MAPPING_ERROR);
        register(UnrecognizedPropertyException.class, ErrorCode.JSON_PROPERTY_UNKNOWN);
        register(IgnoredPropertyException.class, ErrorCode.JSON_PROPERTY_UNKNOWN);
        register(InvalidTypeIdException.class, ErrorCode.JSON_MAPPING_ERROR);

        // ========================================================================
        // [Group 5] Spring Core & Beans
        // ========================================================================
        register(BeansException.class, ErrorCode.SYSTEM_ERROR);
        register(BeanInstantiationException.class, ErrorCode.INSTANTIATION_ERROR);
        register(BeanCreationException.class, ErrorCode.SYSTEM_ERROR);
        register(NoSuchBeanDefinitionException.class, ErrorCode.SYSTEM_ERROR);
        register(TypeMismatchException.class, ErrorCode.PARAM_TYPE_MISMATCH);
        register(ConversionNotSupportedException.class, ErrorCode.PARAM_TYPE_MISMATCH);
        register(ConversionFailedException.class, ErrorCode.PARAM_FORMAT_ERROR);
        register(ApplicationContextException.class, ErrorCode.SYSTEM_ERROR);
        register(TaskRejectedException.class, ErrorCode.THREAD_POOL_EXHAUSTED);
        register(InvalidMimeTypeException.class, ErrorCode.MEDIA_TYPE_NOT_SUPPORTED);

        // ========================================================================
        // [Group 6] Spring Web MVC
        // ========================================================================
        // Params & Binding
        register(MethodArgumentNotValidException.class, ErrorCode.PARAMS_ERROR); // @Valid failed
        register(BindException.class, ErrorCode.PARAMS_ERROR); // Form bind failed
        register(MissingServletRequestParameterException.class, ErrorCode.PARAM_IS_NULL);
        register(MissingServletRequestPartException.class, ErrorCode.MULTI_PART_MISSING);
        register(MissingPathVariableException.class, ErrorCode.PARAM_IS_NULL);
        register(MissingRequestHeaderException.class, ErrorCode.HEADER_MISSING);
        register(MissingRequestCookieException.class, ErrorCode.COOKIE_MISSING);
        register(ServletRequestBindingException.class, ErrorCode.CLIENT_ERROR);

        // Type & Format
        register(MethodArgumentTypeMismatchException.class, ErrorCode.PARAM_TYPE_MISMATCH);
        register(HttpMessageNotReadableException.class, ErrorCode.BODY_NOT_READABLE);
        register(HttpMessageNotWritableException.class, ErrorCode.SYSTEM_ERROR);

        // HTTP Protocol
        register(HttpRequestMethodNotSupportedException.class, ErrorCode.HTTP_METHOD_NOT_SUPPORTED);
        register(HttpMediaTypeNotSupportedException.class, ErrorCode.MEDIA_TYPE_NOT_SUPPORTED);
        register(HttpMediaTypeNotAcceptableException.class, ErrorCode.MEDIA_TYPE_NOT_SUPPORTED);
        register(NoHandlerFoundException.class, ErrorCode.RESOURCE_NOT_FOUND);
        register(AsyncRequestTimeoutException.class, ErrorCode.EXECUTION_TIMEOUT);

        // File Upload
        register(MaxUploadSizeExceededException.class, ErrorCode.FILE_SIZE_EXCEED);
        register(MultipartException.class, ErrorCode.FILE_UPLOAD_ERROR);

        // General Web
        register(ResponseStatusException.class, ErrorCode.CLIENT_ERROR);

        // ========================================================================
        // [Group 7] Spring Security 6.0+
        // ========================================================================
//        register(AuthenticationException.class, ErrorCode.UNAUTHORIZED);
//        register(BadCredentialsException.class, ErrorCode.PASSWORD_ERROR);
//        register(UsernameNotFoundException.class, ErrorCode.ACCOUNT_NOT_FOUND);
//        register(AccountStatusException.class, ErrorCode.ACCOUNT_DISABLED);
//        register(LockedException.class, ErrorCode.ACCOUNT_LOCKED);
//        register(DisabledException.class, ErrorCode.ACCOUNT_DISABLED);
//        register(AccountExpiredException.class, ErrorCode.ACCOUNT_EXPIRED);
//        register(CredentialsExpiredException.class, ErrorCode.CREDENTIALS_EXPIRED);
//        register(InsufficientAuthenticationException.class, ErrorCode.UNAUTHORIZED);
//
//        // Authorization
//        register(AccessDeniedException.class, ErrorCode.FORBIDDEN);
//        register(AuthorizationServiceException.class, ErrorCode.FORBIDDEN);
//        register(AuthorizationDeniedException.class, ErrorCode.FORBIDDEN); // Security 6
//
//        // OAuth2
//        register(OAuth2AuthenticationException.class, ErrorCode.OAUTH2_ERROR);
//        register(InvalidBearerTokenException.class, ErrorCode.TOKEN_INVALID);
//        register(JwtException.class, ErrorCode.TOKEN_INVALID);

        // ========================================================================
        // [Group 8] Spring Data & Database
        // ========================================================================
        register(DataAccessException.class, ErrorCode.DATABASE_ERROR);
        register(UncategorizedDataAccessException.class, ErrorCode.DATABASE_ERROR);

        // Consistency
        register(DuplicateKeyException.class, ErrorCode.DUPLICATE_KEY);
        register(DataIntegrityViolationException.class, ErrorCode.DATA_INTEGRITY_VIOLATION);
        register(CannotAcquireLockException.class, ErrorCode.CANNOT_ACQUIRE_LOCK);
        register(DeadlockLoserDataAccessException.class, ErrorCode.DEADLOCK_DETECTED);
        register(PessimisticLockingFailureException.class, ErrorCode.PESSIMISTIC_LOCK_ERROR);
        register(OptimisticLockingFailureException.class, ErrorCode.OPTIMISTIC_LOCK_ERROR);

        // Result
        register(EmptyResultDataAccessException.class, ErrorCode.DATA_NOT_FOUND);
        register(IncorrectResultSizeDataAccessException.class, ErrorCode.RESULT_SIZE_ERROR);

        // Connectivity
        register(DataAccessResourceFailureException.class, ErrorCode.DB_CONNECTION_ERROR);
        register(CannotGetJdbcConnectionException.class, ErrorCode.ACQUIRE_CONNECTION_TIMEOUT);
        register(QueryTimeoutException.class, ErrorCode.TRANSACTION_TIMEOUT);

        // Transaction
        register(TransactionSystemException.class, ErrorCode.TRANSACTION_ERROR);
        register(TransactionTimedOutException.class, ErrorCode.TRANSACTION_TIMEOUT);

        // SQL Specific
        register(SQLException.class, ErrorCode.DATABASE_ERROR);
        register(SQLSyntaxErrorException.class, ErrorCode.SQL_SYNTAX_ERROR);
        register(SQLIntegrityConstraintViolationException.class, ErrorCode.DATA_INTEGRITY_VIOLATION);
        register(SQLTimeoutException.class, ErrorCode.TRANSACTION_TIMEOUT);
        register(BadSqlGrammarException.class, ErrorCode.BAD_SQL_GRAMMAR);

        // ========================================================================
        // [Group 9] Middleware (Redis, RPC, etc.)
        // ========================================================================
        // Redis
        register(RedisConnectionFailureException.class, ErrorCode.CACHE_CONNECTION_ERROR);
        register(RedisSystemException.class, ErrorCode.CACHE_ERROR);
        register(SerializationException.class, ErrorCode.CACHE_WRITE_ERROR);

        // RestTemplate / WebClient
        register(HttpClientErrorException.class, ErrorCode.RPC_ERROR);
        register(HttpServerErrorException.class, ErrorCode.RPC_ERROR);
        register(ResourceAccessException.class, ErrorCode.RPC_TIMEOUT);
        register(RestClientException.class, ErrorCode.RPC_ERROR);

        // ========================================================================
        // [Group 10] Third Party
        // ========================================================================
        // Validation (Jakarta)
        register(ConstraintViolationException.class, ErrorCode.PARAMS_ERROR);
        register(ValidationException.class, ErrorCode.PARAMS_ERROR);

        // Resilience4j (Circuit Breaker)
//        register(CallNotPermittedException.class, ErrorCode.CIRCUIT_BREAKER_OPEN);
//        register(RequestNotPermitted.class, ErrorCode.RATE_LIMIT_EXCEEDED);
//        register(BulkheadFullException.class, ErrorCode.BULKHEAD_FULL);

        // ========================================================================
        // [Group 11] Fallback (兜底)
        // ========================================================================
        register(UndeclaredThrowableException.class, ErrorCode.SYSTEM_ERROR);
        register(RuntimeException.class, ErrorCode.SYSTEM_ERROR);
        register(Exception.class, ErrorCode.INTERNAL_SERVER_ERROR);
        register(Throwable.class, ErrorCode.INTERNAL_SERVER_ERROR);

        // Optional: Log registration count
        // log.info("ExceptionMappingRegistry initialized with {} mappings.", EXCEPTION_MAPPINGS.size());
    }

    /**
     * 注册辅助方法
     */
    private static void register(Class<? extends Throwable> exceptionClass, ErrorCode errorCode) {
        // 防止重复注册覆盖
        if (EXCEPTION_MAPPINGS.containsKey(exceptionClass)) {
            log.warn("Duplicate exception mapping detected for: {}", exceptionClass.getName());
        }
        EXCEPTION_MAPPINGS.put(exceptionClass, errorCode);
    }

    /**
     * 获取 ErrorCode (高性能/强类型/智能拆包)
     */
    public static ErrorCode getErrorCode(Throwable throwable) {
        if (throwable == null) {
            return ErrorCode.SYSTEM_ERROR;
        }

        // 1. 智能拆包 (Unwrap wrapper exceptions)
        // 处理 Future, Proxy, Servlet, Transaction 包装的异常
        int depth = 0;
        while (depth < 8 && (
                throwable instanceof InvocationTargetException ||
                        throwable instanceof UndeclaredThrowableException ||
                        throwable instanceof ExecutionException ||
                        throwable instanceof CompletionException ||
                        throwable instanceof jakarta.servlet.ServletException ||
                        throwable instanceof TransactionSystemException)) {

            Throwable cause = throwable.getCause();
            if (cause != null && cause != throwable) {
                throwable = cause;
                depth++;
            } else {
                break;
            }
        }

        Class<? extends Throwable> clazz = throwable.getClass();

        // 2. 查缓存 (O(1))
        ErrorCode cached = CACHED_ERROR_CODES.get(clazz);
        if (cached != null) {
            return cached;
        }

        // 3. 查注册表 (遍历继承链)
        ErrorCode match = null;
        Class<?> searchType = clazz;

        while (searchType != null && Throwable.class.isAssignableFrom(searchType)) {
            match = EXCEPTION_MAPPINGS.get(searchType);
            if (match != null) {
                break;
            }
            searchType = searchType.getSuperclass();
        }

        // 默认兜底
        if (match == null) {
            match = ErrorCode.INTERNAL_SERVER_ERROR;
        }

        // 4. 写入缓存 (Wormhole pattern)
        CACHED_ERROR_CODES.put(clazz, match);
        return match;
    }
}
