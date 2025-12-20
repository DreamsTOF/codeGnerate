package com.dream.codegenerate.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码枚举 (Enterprise Edition - 全量扩展版)
 * 包含 160+ 错误定义，支持 JDK 25 / Spring Boot 4.0
 * <p>
 * 错误码分段标准：
 * [0] 成功
 * [10000 - 19999] 客户端与参数错误 (Client & Params)
 * [20000 - 29999] 认证与安全错误 (Auth & Security)
 * [30000 - 39999] 核心业务逻辑错误 (Business: Pay, Order, Flow, Marketing)
 * [40000 - 49999] 数据与资源错误 (DB, File, Resource)
 * [50000 - 59999] 中间件与集成错误 (Redis, MQ, AI, External)
 * [90000 - 99999] 系统与运行时错误 (System, JVM, Network)
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ========================================================================
    // 0. 成功状态
    // ========================================================================
    SUCCESS(0, "操作成功"),

    // ========================================================================
    // 1. 客户端与参数 (10000 - 19999)
    // ========================================================================
    CLIENT_ERROR(10000, "客户端请求异常"),

    // 101xx: 参数基础校验
    PARAMS_ERROR(10100, "参数校验失败"),
    PARAM_IS_NULL(10101, "必填参数为空"),
    PARAM_FORMAT_ERROR(10102, "参数格式错误"),
    PARAM_VALUE_INVALID(10103, "参数值不合法"),
    PARAM_TYPE_MISMATCH(10104, "参数类型不匹配"),
    PARAM_OUT_OF_RANGE(10105, "参数值超出允许范围"),
    PARAM_LENGTH_LIMIT(10106, "参数长度不符合要求"),
    PARAM_COUNT_LIMIT(10107, "参数个数超出限制"),

    // 102xx: HTTP 协议与格式
    BODY_NOT_READABLE(10200, "请求体无法读取"),
    BODY_FORMAT_ERROR(10201, "请求体格式错误(JSON/XML)"),
    HTTP_METHOD_NOT_SUPPORTED(10202, "不支持的HTTP方法"),
    MEDIA_TYPE_NOT_SUPPORTED(10203, "不支持的Content-Type"),
    HEADER_MISSING(10204, "缺少必要的请求头"),
    HEADER_INVALID(10205, "请求头格式无效"),
    COOKIE_MISSING(10206, "缺少必要的Cookie"),
    REQUEST_URI_TOO_LONG(10207, "请求URI过长"),

    // 103xx: JSON 解析细分 (Jackson)
    JSON_PARSE_ERROR(10300, "JSON语法错误，无法解析"),
    JSON_MAPPING_ERROR(10301, "JSON结构与对象不匹配"),
    JSON_PROPERTY_UNKNOWN(10302, "JSON包含未知的属性"),
    JSON_PROPERTY_MISSING(10303, "JSON缺少必要属性"),
    JSON_FORMAT_INVALID(10304, "JSON字段格式转换失败"),

    // 104xx: 文件操作 (本地及上传下载)
    FILE_UPLOAD_ERROR(10400, "文件上传失败"),
    FILE_SIZE_EXCEED(10401, "文件大小超出限制"),
    FILE_TYPE_ERROR(10402, "不支持的文件扩展名"),
    FILE_CONTENT_EMPTY(10403, "文件内容为空"),
    FILE_NAME_INVALID(10404, "文件名包含非法字符"),
    MULTI_PART_MISSING(10405, "Multipart请求部分缺失"),
    FILE_DOWNLOAD_ERROR(10406, "文件下载失败"),
    FILE_NOT_FOUND_LOCAL(10407, "本地文件未找到"),
    FILE_PATH_INVALID(10408, "文件存储路径非法"),
    FILE_READ_PERMISSION_DENIED(10409, "文件读取权限不足"),
    FILE_WRITE_PERMISSION_DENIED(10410, "文件写入权限不足"),
    FILE_CHECKSUM_FAILED(10411, "文件MD5/SHA校验失败"),
    FILE_CORRUPTED(10412, "文件内容已损坏"),
    FILE_ALREADY_EXISTS(10413, "同名文件已存在"),
    FILE_REPLACE_FAILED(10414, "文件替换/覆盖失败"),

    // ========================================================================
    // 2. 认证与安全 (20000 - 29999)
    // ========================================================================
    UNAUTHORIZED(20000, "未认证，请先登录"),
    FORBIDDEN(20001, "权限不足，禁止访问"),
    ANONYMOUS_NOT_ALLOWED(20002, "匿名用户禁止访问"),

    // 201xx: Token 相关
    TOKEN_INVALID(20100, "无效的Token"),
    TOKEN_EXPIRED(20101, "Token已过期"),
    TOKEN_SIGNATURE_ERROR(20102, "Token签名验证失败"),
    TOKEN_MALFORMED(20103, "Token格式错误"),
    TOKEN_MISSING(20104, "Token未提供"),
    REFRESH_TOKEN_EXPIRED(20105, "刷新令牌已过期"),
    REFRESH_TOKEN_INVALID(20106, "刷新令牌无效"),

    // 202xx: 账号与密码
    ACCOUNT_NOT_FOUND(20200, "账号不存在"),
    PASSWORD_ERROR(20201, "密码错误"),
    ACCOUNT_LOCKED(20202, "账号已被锁定"),
    ACCOUNT_DISABLED(20203, "账号已被禁用"),
    ACCOUNT_EXPIRED(20204, "账号已过期"),
    CREDENTIALS_EXPIRED(20205, "凭证已过期，请重置密码"),
    LOGIN_ATTEMPT_LIMIT(20206, "登录尝试次数过多，请稍后再试"),
    USER_ALREADY_EXISTS(20207, "用户已存在"),
    USER_PHONE_EXISTS(20208, "手机号已被占用"),
    USER_EMAIL_EXISTS(20209, "邮箱已被占用"),
    ACCOUNT_FORCE_LOGOUT(20210, "账号被强制登出"),

    // 203xx: OAuth2 / 风控
    OAUTH2_ERROR(20300, "OAuth2认证失败"),
    OAUTH2_SCOPE_ERROR(20301, "授权范围(Scope)不足"),
    OAUTH2_CLIENT_ERROR(20302, "无效的Client ID"),
    CSRF_ERROR(20303, "CSRF校验失败"),
    MFA_REQUIRED(20304, "需要多因素认证(MFA)"),
    IP_BLOCKED(20305, "当前IP已被限制访问"),
    RISK_CONTROL_BLOCK(20306, "触发安全风控，操作被拒绝"),
    SENSITIVE_WORDS_ERROR(20307, "包含敏感词汇"),

    // 204xx: RBAC 细分
    ROLE_NOT_FOUND(20400, "角色不存在"),
    PERMISSION_DENIED(20401, "没有该接口的操作权限"),
    DATA_SCOPE_ERROR(20402, "数据权限越界访问"),

    // ========================================================================
    // 3. 核心业务逻辑 (30000 - 39999)
    // ========================================================================
    BUSINESS_ERROR(30000, "业务处理异常"),
    OPERATION_FAILED(30001, "操作失败"),

    // 301xx: 流程与状态
    STATUS_ERROR(30100, "当前状态不允许执行此操作"),
    FLOW_REJECTED(30101, "流程被驳回"),
    WORKFLOW_ERROR(30102, "工作流执行异常"),
    REPEAT_SUBMIT(30103, "请勿重复提交"),
    IDEMPOTENT_ERROR(30104, "幂等性校验失败"),
    ACTION_TIMEOUT(30105, "操作已超时"),

    // 302xx: 支付与订单
    PAYMENT_FAILED(30200, "支付失败"),
    BALANCE_INSUFFICIENT(30201, "余额不足"),
    ORDER_NOT_FOUND(30202, "订单不存在"),
    ORDER_CLOSED(30203, "订单已关闭"),
    ORDER_EXPIRED(30204, "订单已过期"),
    COUPON_INVALID(30205, "优惠券无效"),
    INVENTORY_SHORTAGE(30206, "商品库存不足"),
    REFUND_FAILED(30207, "退款操作失败"),
    PAY_PASSWORD_ERROR(30208, "支付密码错误"),
    CURRENCY_NOT_SUPPORTED(30209, "不支持的结算货币"),

    // 303xx: 消息/验证码
    CAPTCHA_ERROR(30300, "验证码错误"),
    CAPTCHA_EXPIRED(30301, "验证码已过期"),
    SMS_SEND_FAILED(30302, "短信发送失败"),
    SMS_CODE_INVALID(30303, "短信验证码无效"),
    SMS_SEND_TOO_FREQUENT(30304, "短信发送过于频繁"),
    EMAIL_SEND_FAILED(30305, "邮件发送失败"),
    PUSH_TOKEN_INVALID(30306, "推送Token失效"),

    // 304xx: 营销与会员
    COUPON_ALREADY_USED(30400, "优惠券已被使用"),
    COUPON_NOT_STARTED(30401, "活动尚未开始"),
    COUPON_LIMIT_EXCEEDED(30402, "领取已达上限"),
    ACTIVITY_ENDED(30403, "活动已结束"),
    POINTS_INSUFFICIENT(30404, "积分余额不足"),
    MEMBER_LEVEL_LOW(30405, "会员等级不足"),

    // 305xx: 物流与地理
    LOGISTICS_NOT_FOUND(30500, "物流单号不存在"),
    ADDRESS_INVALID(30501, "无效的收货地址"),
    GEO_LOCATION_ERROR(30502, "地理位置解析失败"),

    // ========================================================================
    // 4. 数据与资源 (40000 - 49999)
    // ========================================================================
    DATABASE_ERROR(40000, "数据库服务异常"),
    SQL_SYNTAX_ERROR(40001, "SQL语法错误"),
    BAD_SQL_GRAMMAR(40002, "非法的SQL语句"),

    // 401xx: 约束与完整性
    DUPLICATE_KEY(40100, "数据已存在(违反唯一约束)"),
    DATA_INTEGRITY_VIOLATION(40101, "违反数据完整性约束"),
    FOREIGN_KEY_VIOLATION(40102, "外键关联错误"),
    DATA_TOO_LONG(40103, "数据长度超出字段限制"),

    // 402xx: 连接与事务
    DB_CONNECTION_ERROR(40200, "数据库连接失败"),
    ACQUIRE_CONNECTION_TIMEOUT(40201, "获取数据库连接超时"),
    TRANSACTION_ERROR(40202, "事务执行失败"),
    TRANSACTION_TIMEOUT(40203, "事务执行超时"),
    DEADLOCK_DETECTED(40204, "检测到数据库死锁"),
    ROLLBACK_ERROR(40205, "事务回滚异常"),

    // 403xx: 锁与并发
    PESSIMISTIC_LOCK_ERROR(40300, "获取悲观锁失败"),
    OPTIMISTIC_LOCK_ERROR(40301, "数据已被其他用户修改(乐观锁失败)"),
    CANNOT_ACQUIRE_LOCK(40302, "无法获取资源锁，请稍后再试"),
    DISTRIBUTED_LOCK_EXPIRED(40303, "分布式锁已过期自动释放"),

    // 404xx: 资源查询
    RESOURCE_NOT_FOUND(40400, "请求的资源不存在"),
    RESOURCE_ALREADY_EXISTS(40401, "资源名称已存在"),
    DATA_NOT_FOUND(40402, "查询结果为空"),
    RESULT_SIZE_ERROR(40403, "返回数据行数不符合预期"),

    // ========================================================================
    // 5. 中间件与集成 (50000 - 59999)
    // ========================================================================
    CACHE_ERROR(50000, "缓存服务异常"),
    CACHE_CONNECTION_ERROR(50001, "缓存连接失败"),
    CACHE_READ_ERROR(50002, "缓存读取超时"),
    CACHE_WRITE_ERROR(50003, "缓存写入失败"),
    KEY_EXPIRED(50004, "缓存Key已过期"),
    CACHE_PENETRATION(50005, "触发缓存穿透保护"),

    // 501xx: MQ (Kafka/Rabbit)
    MQ_ERROR(50100, "消息队列服务异常"),
    MQ_SEND_ERROR(50101, "消息投递失败"),
    MQ_CONSUME_ERROR(50102, "消息消费失败"),
    MQ_BROKER_UNAVAILABLE(50103, "消息中间件不可用"),

    // 502xx: Search (ES)
    SEARCH_ENGINE_ERROR(50200, "搜索引擎异常"),
    INDEX_NOT_FOUND(50201, "索引不存在"),
    SEARCH_SYNTAX_ERROR(50202, "搜索语法错误"),
    INDEX_MAPPING_ERROR(50203, "字段映射错误"),

    // 503xx: RPC / Feign
    RPC_ERROR(50300, "远程服务调用失败"),
    RPC_TIMEOUT(50301, "远程调用超时"),
    RPC_CONNECTION_REFUSED(50302, "远程连接被拒绝"),
    RPC_NOT_FOUND(50303, "远程服务实例未找到"),
    SERVICE_VERSION_MISMATCH(50304, "接口版本不匹配"),

    // 504xx: 限流与稳定性
    CIRCUIT_BREAKER_OPEN(50400, "服务熔断中"),
    RATE_LIMIT_EXCEEDED(50401, "请求过于频繁(限流)"),
    BULKHEAD_FULL(50402, "并发请求量过大(舱壁已满)"),
    DEGRADE_ERROR(50403, "触发降级策略"),

    // 505xx: AI / LLM
    AI_SERVICE_ERROR(50500, "AI服务响应异常"),
    AI_MODEL_NOT_FOUND(50501, "模型不存在"),
    AI_QUOTA_EXCEEDED(50502, "Token配额耗尽"),
    AI_CONTENT_VIOLATION(50503, "触发AI安全内容合规"),
    AI_OUTPUT_ERROR(50504, "结果解析失败"),
    AI_CONTEXT_TOO_LONG(50505, "上下文长度超出模型限制"),

    // 506xx: OSS / Cloud Storage
    OSS_ERROR(50600, "对象存储服务异常"),
    OSS_FILE_NOT_FOUND(50601, "云端文件不存在"),
    OSS_UPLOAD_ERROR(50602, "云端上传失败"),
    OSS_SIGNATURE_EXPIRED(50603, "云端访问签名过期"),
    OSS_BUCKET_NOT_FOUND(50604, "存储桶未创建或无权限"),

    // 507xx: 微服务基座
    CONFIG_NOT_FOUND(50700, "配置中心未找到对应Key"),
    CONFIG_SERVER_ERROR(50701, "配置中心连接失败"),
    GATEWAY_ERROR(50800, "网关转发异常"),
    GATEWAY_TIMEOUT(50801, "网关请求下游服务超时"),

    // ========================================================================
    // 9. 系统与运行时 (90000 - 99999)
    // ========================================================================
    SYSTEM_ERROR(90000, "系统内部异常"),
    INTERNAL_SERVER_ERROR(90001, "服务器内部未知错误"),

    // 901xx: Java Runtime
    NULL_POINTER(90100, "空指针异常"),
    CAST_ERROR(90101, "类型转换错误"),
    INDEX_OUT_OF_BOUNDS(90102, "数组或集合越界"),
    ARITHMETIC_ERROR(90103, "算术运算错误"),
    ASSERT_ERROR(90104, "断言失败"),
    CLONE_ERROR(90105, "对象克隆失败"),
    STACK_OVERFLOW(90106, "栈溢出异常"),

    // 902xx: IO & Net
    IO_ERROR(90200, "I/O 操作异常"),
    FILE_NOT_FOUND(90201, "底层文件系统未找到指定文件"),
    NETWORK_ERROR(90202, "网络通讯异常"),
    SOCKET_TIMEOUT(90203, "Socket连接/读取超时"),
    UNKNOWN_HOST(90204, "无法解析目标主机"),
    SSL_ERROR(90205, "SSL证书校验异常"),

    // 903xx: 并发
    CONCURRENT_ERROR(90300, "并发执行异常"),
    THREAD_INTERRUPTED(90301, "线程被中断"),
    THREAD_POOL_EXHAUSTED(90302, "系统线程池已耗尽"),
    EXECUTION_TIMEOUT(90303, "任务执行超时"),
    FUTURE_ERROR(90304, "异步任务结果获取异常"),

    // 904xx: 反射与类加载
    CLASS_NOT_FOUND(90400, "类定义未找到"),
    METHOD_NOT_FOUND(90401, "方法未找到"),
    FIELD_NOT_FOUND(90402, "字段未找到"),
    INSTANTIATION_ERROR(90403, "对象实例化失败"),

    // 905xx: 新特性异常
    MATCH_ERROR(90500, "模式匹配失败(Switch Case)"),
    VIRTUAL_THREAD_ERROR(90501, "虚拟线程执行异常"),
    STRUCTURED_CONCURRENCY_ERROR(90502, "结构化并发约束违例"),

    // 906xx: 资源与硬件
    DISK_FULL(90600, "服务器磁盘空间不足"),
    MEMORY_EXHAUSTED(90601, "JVM内存不足(OOM)"),
    CPU_OVERLOAD(90602, "服务器CPU负载过载"),
    TEMP_DIR_CLEAN_ERROR(90603, "临时目录清理失败"),

    // 907xx: 安全审计
    ENCRYPTION_ERROR(90700, "数据加密失败"),
    DECRYPTION_ERROR(90701, "数据解密失败"),
    SIGNATURE_INVALID(90702, "数字签名无效"),
    CRYPTO_ALGORITHM_NOT_FOUND(90703, "不支持的加密算法");

    private final int code;
    private final String message;

    /**
     * 根据code获取枚举值
     */
    public static ErrorCode getByCode(int code) {
        for (ErrorCode ec : ErrorCode.values()) {
            if (ec.getCode() == code) {
                return ec;
            }
        }
        return SYSTEM_ERROR;
    }
}
