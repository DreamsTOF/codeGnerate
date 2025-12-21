package com.dream.codegenerate.utils;

import cn.hutool.v7.core.bean.BeanUtil;
import cn.hutool.v7.core.collection.CollUtil;
import cn.hutool.v7.core.text.StrUtil;
import cn.hutool.v7.core.reflect.ConstructorUtil;
import cn.hutool.v7.core.util.ObjUtil;

import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.mapper.UniversalSearchMapper;
import com.dream.codegenerate.log.core.LogLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.mybatis.Mappers;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.query.QueryMethods;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import com.mybatisflex.core.util.LambdaGetter;
import com.mybatisflex.core.util.LambdaUtil;

import jakarta.annotation.PostConstruct;
import lombok.CustomLog;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import java.beans.PropertyDescriptor;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>
 * 🚀 <b>FlexSmartQuery - 强类型 Lambda 驱动智能查询引擎 (Ultimate Platinum Edition)</b>
 * </p>
 *
 * <h2>一、 核心设计哲学 (Design Philosophy)</h2>
 * <p>
 * 在现代企业级应用开发中，查询逻辑往往占据了 60% 以上的代码量。传统的 MyBatis 开发模式存在以下痛点：
 * <ol>
 * <li><b>冗余的判空逻辑：</b> 到处充斥着 {@code if (str != null && !str.isEmpty())} 这样的防御性代码。</li>
 * <li><b>字符串硬编码：</b> {@code qw.eq("user_id", val)} 导致重构困难，字段改名即报错。</li>
 * <li><b>N+1 性能陷阱：</b> 在循环中调用 RPC 或 SQL 查询关联数据，导致数据库连接池瞬间耗尽。</li>
 * <li><b>DTO/VO 割裂：</b> 入参 DTO 与出参 VO 之间的转换逻辑分散在 Service 层的各个角落。</li>
 * </ol>
 * <b>FlexSmartQuery</b> 正是为了解决上述问题而生。它引入了“语义化查询”、“声明式回填”和“全 Lambda 约束”三大核心概念，
 * 将查询逻辑从过程式代码转变为声明式元数据，实现“一枪流”查询。
 * </p>
 *
 * <h2>二、 极致复杂的应用场景 (Hardcore Scenarios)</h2>
 *
 * <h3>场景 1：三表深度级联回填 (Chain Hydration)</h3>
 * <p>
 * <b>业务背景：</b> 用户列表展示时，需要显示用户所属的部门名称，以及该部门所属的公司名称。
 * 数据库中 {@code User} 存了 {@code deptId}，{@code Dept} 存了 {@code companyId}。
 * </p>
 * <pre>{@code
 * List<UserVO> list = FlexSmartQuery.of(User.class)
 * .autoBind(UserVO.class)
 * // 1. 第一层绑定：通过 User.deptId -> Dept.id，抓取 Dept.name 填入 UserVO.deptName
 * .bind(User::getDeptId, Dept::getId, Dept::getName, UserVO::setDeptName, MatchType.EQ)
 * // 2. 中间层数据获取：通过 User.deptId -> Dept.id，抓取 Dept.companyId 填入 UserVO.companyId (作为下一层的 Key)
 * .bind(User::getDeptId, Dept::getId, Dept::getCompanyId, UserVO::setCompanyId, null)
 * // 3. 第二层级联绑定：通过 UserVO.companyId -> Company.id，抓取 Company.name 填入 UserVO.companyName
 * .bind(UserVO::getCompanyId, Company::getId, Company::getName, UserVO::setCompanyName, null)
 * .list();
 * }</pre>
 *
 * <h3>场景 2：1:N 集合智能汇聚 (One-to-Many Hydration)</h3>
 * <p>
 * <b>业务背景：</b> 一个用户可能拥有多个角色。在列表查询时，希望将该用户的所有角色名称聚合为一个 List 显示。
 * </p>
 * <pre>{@code
 * public class UserVO {
 * private Long id;
 * // 定义为 List 类型，引擎会自动识别并启用聚合模式
 * private List<String> roleNames;
 * }
 *
 * List<UserVO> list = FlexSmartQuery.of(User.class)
 * .autoBind(UserVO.class)
 * // MatchType.LIKE 或 EQ 均可，只要关联关系是一对多，结果就会被自动收集到 roleNames 集合中
 * .bind(User::getId, UserRole::getUserId, UserRole::getRoleName, UserVO::setRoleNames, MatchType.EQ)
 * .list();
 * }</pre>
 *
 * <h3>场景 3：高敏数据动态脱敏 (Dynamic Masking)</h3>
 * <p>
 * <b>业务背景：</b> 在导出或展示时，需要对身份证号进行脱敏，且不允许前端通过 API 传入密码字段进行过滤。
 * </p>
 * <pre>{@code
 * FlexSmartQuery.of(User.class)
 * // 1. 安全防御：禁止前端通过 DTO 传入 password 和 salt 字段，防止恶意猜测
 * .ignore(UserDTO::getPassword, UserDTO::getSalt)
 * // 2. 自定义回调：在回填过程中，直接编写 Java 逻辑进行数据处理
 * .bind(User::getId, UserExt::getUserId, UserExt::getIdCard, (vo, val) -> {
 * String masked = DesensitizedUtil.idCard(val.toString());
 * vo.setIdCard(masked);
 * }, null)
 * .autoBuild(queryDto)
 * .list();
 * }</pre>
 *
 * <h3>场景 4：异构数据源 JSON 自动映射</h3>
 * <p>
 * <b>业务背景：</b> 某些扩展配置存储在 MongoDB 或 MySQL 的 JSON 字段中，查询时需要自动反序列化为对象。
 * </p>
 * <pre>{@code
 * // 只要目标属性 ConfigDTO 是复杂对象，引擎会自动调用 ObjectMapper 进行转换
 * @Relation(targetEntity = SysConfig.class, localField = "configId", remoteField = "jsonContent")
 * private ConfigDTO config;
 * }</pre>
 *
 * <h2>三、 核心架构图解 (Architecture)</h2>
 * <pre>
 * [Request DTO] --> [Semantic Parser] --> [QueryWrapper Builder] --> [MyBatis-Flex Executor]
 * |
 * (List<Entity>)
 * |
 * v
 * [Result Transformer] <-- [Hydration Engine (Batch Fetch)] <-- [ID Aggregator (Distinct)]
 * |
 * +--> [1:1 Binder]
 * +--> [1:N Collection Binder]
 * +--> [JSON Deserializer]
 * |
 * v
 * [List<VO>]
 * </pre>
 *
 * @param <E> 数据库物理实体类类型 (Entity)，必须被 {@code @Table} 注解标记
 * @param <R> 返回结果集类型 (Result)，可以是 Entity 本身，也可以是 VO/DTO
 *
 * @author Gemini & Architect Team
 * @version 5.0.0-GA (The Perfect Form)
 * @since 2023-12
 */
@CustomLog
public class FlexSmartQuery<E, R> {

    /* ==============================================================================
     * SECTION 1: 核心系统级硬限制与常量定义 (System Constants)
     * ------------------------------------------------------------------------------
     * 本部分定义了系统的物理边界，任何业务逻辑都不得突破这些红线，以保障系统稳定性。
     * ============================================================================== */

    /**
     * <h3>系统级最大分页限制</h3>
     * <p>
     * 即使前端传递了 {@code pageSize=10000}，系统也会强制截断为此值。
     * 这是防止 {@code OutOfMemoryError} 的最后一道防线。
     * </p>
     * <b>性能考量：</b> 超过 1000 条的数据在前端展示通常无意义，且会占用大量 JVM 堆内存用于对象转换。
     */
    private static final int SYSTEM_MAX_PAGE_SIZE = 1000;

    /**
     * <h3>关联回填批处理大小</h3>
     * <p>
     * 当执行跨表查询时，引擎会收集主表中的 ID 列表，并生成 {@code WHERE id IN (...)} 语句。
     * 此常量控制 IN 子句中的参数个数。
     * </p>
     * <b>数据库限制：</b> Oracle 数据库限制 IN 列表最大为 1000，PostgreSQL 和 MySQL 虽无硬性限制但过长会导致解析变慢。
     * <b>优化策略：</b> 将大列表切分为多个小批次（Partition），并行或串行执行。
     */
    private static final int HYDRATION_BATCH_SIZE = 500;

    /**
     * <h3>模糊查询安全长度限制</h3>
     * <p>
     * 用于 {@code LIKE} 查询的输入字符串最大长度。
     * </p>
     * <b>安全风险：</b> 恶意用户构造超长字符串（如 1MB 的文本）进行模糊匹配，会导致数据库 CPU 瞬间飙升至 100%。
     */
    private static final int MAX_LIKE_INPUT_LENGTH = 64;

    /**
     * <h3>基础数据类型白名单</h3>
     * <p>
     * 用于判断从数据库取出的数据是否需要进行 JSON 反序列化。
     * </p>
     * <b>判断逻辑：</b> 如果目标字段类型在此集合中，则直接赋值或简单类型转换；否则，视为复杂对象交给 Jackson 处理。
     */
    private static final Set<Class<?>> PRIMITIVE_LIKE_TYPES = Set.of(
            String.class, Long.class, Integer.class, Short.class, Byte.class, Double.class, Float.class,
            Boolean.class, Character.class, BigDecimal.class, java.util.Date.class, LocalDate.class, LocalDateTime.class
    );

    /* ==============================================================================
     * SECTION 2: 声明式协议注解 (Protocol Annotations)
     * ------------------------------------------------------------------------------
     * 这部分注解构成了 DTO/VO 与查询引擎之间的通信协议。
     * ============================================================================== */

    /**
     * <h3>关联关系定义注解</h3>
     * <p>
     * 标注在 VO 类的字段上，用于描述该字段的数据来源。
     * 相比于编程式的 {@code bind()} 方法，注解方式更加静态和直观，适合固定的业务关系。
     * </p>
     */
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Relation {
        /**
         * <b>目标实体类：</b>
         * 指向提供数据的远程表实体类。该类必须被 MyBatis-Flex 的 {@code @Table} 注解标记。
         */
        Class<?> targetEntity();

        /**
         * <b>本地关联键：</b>
         * 当前 VO 或 Entity 中存储外键值的属性名（例如 "deptId"）。
         */
        String localField();

        /**
         * <b>远程关联键：</b>
         * 目标表中用于匹配的列名。
         * <p>默认值为空字符串，此时引擎会自动获取目标实体的 {@code @Id} 主键列。</p>
         */
        String remoteFieldLink() default "";

        /**
         * <b>目标提取列：</b>
         * 目标表中真正想要展示的数据列名（例如 "deptName"）。
         */
        String remoteField();

        /**
         * <b>反向过滤类型：</b>
         * 当在 DTO 中对该字段进行筛选时（如筛选 deptName），引擎如何处理。
         * <p>默认为 {@code CUSTOM}，即只做回填，不参与查询条件的构建。</p>
         */
        MatchType matchType() default MatchType.CUSTOM;
    }

    /** 标注在 DTO 字段上，用于自动识别“页码”参数 */
    @Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME) public @interface PageNo {}

    /** 标注在 DTO 字段上，用于自动识别“每页条数”参数 */
    @Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME) public @interface PageSize {}

    /** 标注在 DTO 字段上，用于自动识别“排序字段”参数 */
    @Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME) public @interface SortField {}

    /** 标注在 DTO 字段上，用于自动识别“排序方向 (ASC/DESC)”参数 */
    @Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME) public @interface SortOrder {}

    /* ==============================================================================
     * SECTION 3: 内部领域模型 (Internal Domain Models)
     * ------------------------------------------------------------------------------
     * 定义了引擎运行所需的内部数据结构。
     * ============================================================================== */

    /**
     * <h3>SQL 匹配模式枚举</h3>
     * 定义了 Java 语义到 SQL 操作符的映射关系。
     */
    public enum MatchType {
        /** SQL: {@code = ?} */
        EQ,
        /** SQL: {@code <> ?} */
        NE,
        /** SQL: {@code >= ?} */
        GE,
        /** SQL: {@code > ?} */
        GT,
        /** SQL: {@code <= ?} */
        LE,
        /** SQL: {@code < ?} */
        LT,
        /** SQL: {@code LIKE %?%} */
        LIKE,
        /** SQL: {@code LIKE %?} */
        LEFT_LIKE,
        /** SQL: {@code LIKE ?%} */
        RIGHT_LIKE,
        /** SQL: {@code NOT LIKE %?%} */
        NOT_LIKE,
        /** SQL: {@code IN (?, ?, ...)} */
        IN,
        /** SQL: {@code NOT IN (?, ?, ...)} */
        NOT_IN,
        /** 自定义模式，仅保留元数据，不自动生成 SQL */
        CUSTOM
    }

    /**
     * <h3>回填任务描述符 (Extraction Task)</h3>
     * <p>
     * 每一个 {@code bind()} 调用或 {@code @Relation} 注解最终都会被解析为一个 ExtractionTask 对象。
     * 它包含了执行一次数据回填所需的所有元数据。
     * </p>
     */
    private static class ExtractionTask {
        /** 远程数据库表名 */
        String tableName;
        /** 远程表的关联列名 (Key) */
        String linkCol;
        /** 远程表的目标值列名 (Value) */
        String targetCol;
        /** 本地对象的属性名，用于提取外键值 */
        String localProp;
        /** 结果注入器，通常是一个反射 Setter 的封装 */
        BiConsumer<Object, Object> binder;
        /** 目标属性的 Java 类型，用于类型转换判断 */
        Class<?> targetType;
        /** 远程实体类的引用，用于辅助元数据查询 */
        Class<?> remoteEntityClass;
        /** 复杂对象标记，为 true 时表示需要进行 JSON 反序列化 */
        boolean isComplex;
    }

    /**
     * <h3>自定义构建规则 (Build Rule)</h3>
     * <p>
     * 用于存储通过 {@code overrides} 机制覆盖的字段处理逻辑。
     * 当默认的语义分析无法满足需求时，使用此规则接管。
     * </p>
     */
    private record Rule(
            QueryColumn column,
            MatchType type,
            BiFunction<QueryColumn, Object, QueryCondition> customFn
    ) {}

    /**
     * <h3>Spring 容器自动装配配置类</h3>
     * <p>
     * 这是一个内嵌的 {@code @Configuration} 类。
     * 它的作用是利用 Spring 的生命周期，在应用启动时自动获取 {@code UniversalSearchMapper} 和 {@code ObjectMapper} 的 Bean 实例，
     * 并注入到 {@code FlexSmartQuery} 的静态变量中。
     * </p>
     * <b>设计理由：</b> 使得 {@code FlexSmartQuery} 可以作为静态工具类使用（{@code FlexSmartQuery.of(...)}），
     * 而无需让调用者每次都手动注入依赖。
     */
    @Configuration
    @ConditionalOnBean({UniversalSearchMapper.class, ObjectMapper.class})
    public static class SpringAutoConfig {
        private final UniversalSearchMapper mapper;
        private final ObjectMapper objectMapper;

        public SpringAutoConfig(UniversalSearchMapper mapper, ObjectMapper objectMapper) {
            this.mapper = mapper;
            this.objectMapper = objectMapper;
        }

        /**
         * 利用 {@code @PostConstruct} 钩子完成静态注入。
         */
        @PostConstruct
        public void setupFlexContext() {
            FlexSmartQuery.init(mapper, objectMapper);
        }
    }

    /* ==============================================================================
     * SECTION 4: 运行时上下文与缓存 (Runtime Context & Cache)
     * ------------------------------------------------------------------------------
     * 管理全剧静态资源以及当前查询实例的状态。
     * ============================================================================== */

    /** 全局 SQL 执行器，执行动态构建的 SQL */
    private static volatile UniversalSearchMapper searchMapper;
    /** 全局 JSON 处理器，用于复杂对象转换 */
    private static volatile ObjectMapper globalObjectMapper;
    /** 表结构元数据缓存，Key 为实体类 Class */
    private static final Map<Class<?>, TableInfo> LOCAL_TABLE_INFO_MIRROR = new ConcurrentHashMap<>();

    // --- 实例级变量 ---

    /** 当前查询的主实体类型 */
    private final Class<E> entityClass;
    /** 当前查询的返回结果类型 */
    private final Class<R> resultClass;
    /** MyBatis-Flex 的查询包装器 */
    private final QueryWrapper queryWrapper;
    /** 当前主表的元数据信息 */
    private final TableInfo tableInfo;

    /** 待执行的回填任务队列，使用 LinkedHashMap 保证执行顺序 */
    private final Map<String, ExtractionTask> hydrationTasks = new LinkedHashMap<>();
    /** 字段处理覆盖规则表 */
    private final Map<String, Rule> overrides = new HashMap<>();
    /** 指定 SELECT 的列集合 */
    private final Set<QueryColumn> selectColumns = new LinkedHashSet<>();
    /** 字段黑名单，用于 ignore 功能 */
    private final Set<String> blackListFields = new HashSet<>();
    /** 条件去重记录器，防止重复添加相同的 SQL 条件 */
    private final Map<QueryColumn, List<Object>> conditionRecord = new HashMap<>();
    /** 逻辑连接符开关，true 表示使用 OR 连接 */
    private boolean useOrLogic = false;

    /* ==============================================================================
     * SECTION 5: 初始化与实例构建 (Instantiation)
     * ------------------------------------------------------------------------------
     * 包含构造函数、静态工厂方法以及依赖注入接口。
     * ============================================================================== */

    /**
     * <h3>私有构造函数</h3>
     * <p>
     * 初始化一个新的查询构建上下文。
     * 在此阶段会进行严格的依赖检查，如果 Spring 容器未正确初始化核心组件，将抛出运行时异常。
     * </p>
     *
     * @param entityClass 数据库表对应的实体类
     * @param resultClass 期望返回的结果类
     */
    private FlexSmartQuery(Class<E> entityClass, Class<R> resultClass) {
        // 1. 核心依赖守护检查
        if (searchMapper == null || globalObjectMapper == null) {
            log.error("❌ [FlexSmartQuery] 核心组件缺失！UniversalSearchMapper 或 ObjectMapper 未注入。");
            throw new RuntimeException("FlexSmartQuery 引擎未正确初始化，请检查 Spring 配置。");
        }

        this.entityClass = entityClass;
        this.resultClass = resultClass;

        // 2. 获取并缓存元数据 (Thread-Safe with ComputeIfAbsent)
        this.tableInfo = LOCAL_TABLE_INFO_MIRROR.computeIfAbsent(entityClass, TableInfoFactory::ofEntityClass);

        if (this.tableInfo == null) {
            throw new RuntimeException("实体类 [" + entityClass.getName() + "] 未被 @Table 注解标记，无法生成查询。");
        }

        // 3. 初始化 QueryWrapper，默认绑定主表
        this.queryWrapper = QueryWrapper.create().from(tableInfo.getTableName());
    }

    /**
     * <h3>静态工厂入口</h3>
     * <p>
     * 创建一个针对指定实体类的查询流。这是使用本组件的标准起点。
     * </p>
     *
     * @param <E> 实体泛型
     * @param entityClass 实体类 Class 对象
     * @return 一个新的 FlexSmartQuery 实例
     */
    public static <E> FlexSmartQuery<E, E> of(Class<E> entityClass) {
        return new FlexSmartQuery<>(entityClass, entityClass);
    }

    /**
     * <h3>静态资源注入接口</h3>
     * <p>
     * 供 SpringAutoConfig 调用，完成静态单例的注入。
     * </p>
     *
     * @param mapper 全局 Mapper
     * @param mapper2 全局 ObjectMapper
     */
    public static void init(UniversalSearchMapper mapper, ObjectMapper mapper2) {
        searchMapper = mapper;
        globalObjectMapper = mapper2;
    }

    /* ==============================================================================
     * SECTION 6: 强类型链式配置接口 (Fluent Configuration API)
     * ------------------------------------------------------------------------------
     * 提供了一系列流式方法来配置查询行为，如日志、逻辑开关、字段过滤等。
     * ============================================================================== */

    /**
     * <h3>开启 SQL 日志 (Critical Level)</h3>
     * <p>
     * 快捷方法，将生成的 SQL 以最高级别 (Critical) 打印到控制台，确保在任何日志级别下都可见。
     * 常用于开发调试阶段。
     * </p>
     *
     * @return 当前实例
     */
    public FlexSmartQuery<E, R> log() { return log(LogLevel.CRITICAL); }

    /**
     * <h3>开启 SQL 日志 (指定级别)</h3>
     * <p>
     * 获取当前 QueryWrapper 生成的 SQL 语句，并记录到日志系统。
     * </p>
     *
     * @param level 指定的日志级别
     * @return 当前实例
     */
    public FlexSmartQuery<E, R> log(LogLevel level) {
        if (level == null) return this;
        String sql = "\n🔍 [FlexSmartQuery] SQL Snapshot:\n" + this.toSQL() + "\n";
        switch (level) {
            case DEBUG -> log.debug(sql);
            case INFO -> log.info(sql);
            case WARN -> log.warn(sql);
            default -> log.critical(sql);
        }
        return this;
    }

    /**
     * <h3>切换 OR 逻辑模式</h3>
     * <p>
     * 默认情况下，{@code autoBuild} 生成的所有条件通过 {@code AND} 连接。
     * 调用此方法后，后续生成的条件将通过 {@code OR} 连接。
     * </p>
     *
     * @return 当前实例
     */
    public FlexSmartQuery<E, R> useOr() { this.useOrLogic = true; return this; }

    /**
     * <h3>字段黑名单排除 (Type-Safe Ignore)</h3>
     * <p>
     * 指定在 {@code autoBuild} 过程中需要忽略的 DTO 字段。
     * 即使 DTO 中这些字段有值，也不会生成对应的 SQL 条件。
     * </p>
     * <b>使用场景：</b> DTO 中包含密码、盐值或仅仅是前端展示用的字段。
     *
     * @param <D> DTO 类型
     * @param getters 需要排除的字段 Getter 方法引用，如 {@code UserDTO::getPassword}
     * @return 当前实例
     */
    @SafeVarargs
    public final <D> FlexSmartQuery<E, R> ignore(LambdaGetter<D>... getters) {
        if (getters != null) {
            for (LambdaGetter<D> getter : getters) {
                this.blackListFields.add(LambdaUtil.getFieldName(getter));
            }
        }
        return this;
    }

    /**
     * <h3>智能投影限制 (Smart Projection)</h3>
     * <p>
     * 根据传入的类（通常是 VO）中定义的字段，自动生成 SQL 的 {@code SELECT} 子句。
     * </p>
     * <b>性能优化：</b> 避免 {@code SELECT *}，减少不必要的大字段（如 Text/Blob）传输，显著降低 IO 开销。
     *
     * @param clazz 包含目标字段的类
     * @return 当前实例
     */
    public FlexSmartQuery<E, R> select(Class<?> clazz) {
        try {
            // 使用 Hutool 反射工具实例化对象并获取 MetaObject
            MetaObject meta = SystemMetaObject.forObject(ConstructorUtil.newInstance(clazz));
            for (String p : meta.getGetterNames()) {
                // 尝试在主表元数据中查找对应的列
                QueryColumn col = tableInfo.getQueryColumnByProperty(p);
                if (col != null) this.selectColumns.add(col);
            }
        } catch (Exception e) {
            log.error("❌ [FlexSmartQuery] 投影解析失败，类名: {}", clazz.getName(), e);
            throw new RuntimeException("动态投影解析失败，请检查 VO 类是否包含无参构造函数。");
        }
        return this;
    }

    /**
     * <h3>原生 Wrapper 增强接口 (Escape Hatch)</h3>
     * <p>
     * 允许开发者直接访问并修改底层的 {@code QueryWrapper} 对象。
     * <br>适用于 {@code autoBuild} 无法覆盖的复杂场景，如：
     * <ul>
     * <li>复杂的嵌套逻辑：{@code (A or B) and (C or D)}</li>
     * <li>聚合查询：{@code GROUP BY}, {@code HAVING}</li>
     * <li>物理连接：{@code LEFT JOIN}</li>
     * </ul>
     * </p>
     *
     * @param c QueryWrapper 消费者
     * @return 当前实例
     */
    public FlexSmartQuery<E, R> customize(Consumer<QueryWrapper> c) {
        c.accept(this.queryWrapper);
        return this;
    }

    /**
     * <h3>手动追加查询条件</h3>
     * <p>
     * 直接向查询中添加一个 MyBatis-Flex 的 {@code QueryCondition}。
     * <br><b>智能特性：</b> 该方法会自动感知 {@code useOrLogic} 开关。
     * 如果当前处于 OR 模式，则通过 {@code OR} 连接；否则通过 {@code AND} 连接。
     * </p>
     *
     * @param condition 条件对象 (例如: {@code User.AGE.ge(18)})
     * @return 当前实例
     */
    public FlexSmartQuery<E, R> where(QueryCondition condition) {
        applyToWrapper(condition);
        return this;
    }

    /* ==============================================================================
     * SECTION 7: 数据回填与关系引擎 (Hydration Engine Core)
     * ------------------------------------------------------------------------------
     * 这是本组件的心脏。通过解析 Lambda 或注解，构建跨表数据映射逻辑。
     * 包含 1:1 和 1:N 两种核心模式。
     * ============================================================================== */

    /**
     * <h3>VO 自动化绑定分析</h3>
     * <p>
     * 这是一个“一步到位”的方法。它完成了以下动作：
     * <ol>
     * <li>将结果集泛型 {@code <R>} 切换为指定的 {@code <V>}。</li>
     * <li>解析 {@code <V>} 类中所有的 {@code @Relation} 注解。</li>
     * <li>将解析出的关系注册为 Hydration 任务。</li>
     * <li>自动调用 {@code select(voClass)} 优化投影。</li>
     * </ol>
     * </p>
     *
     * @param <V> 新的 VO 类型
     * @param voClass VO 类 Class 对象
     * @return 切换了泛型后的查询实例
     */
    public <V> FlexSmartQuery<E, V> autoBind(Class<V> voClass) {
        // 1. 创建新实例并迁移状态
        FlexSmartQuery<E, V> next = new FlexSmartQuery<>(this.entityClass, voClass);
        BeanUtil.copyProperties(this, next, "resultClass", "hydrationTasks", "selectColumns");

        // 2. 自动应用投影
        next.select(voClass);

        // 3. 扫描注解并注册任务
        for (Field field : voClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Relation.class)) {
                Relation rel = field.getAnnotation(Relation.class);

                // 校验本地字段存在性
                QueryColumn localCol = tableInfo.getQueryColumnByProperty(rel.localField());
                if (localCol == null) {
                    log.warn("⚠️ [Relation] 忽略无效绑定：实体 {} 中不存在属性 {}", entityClass.getSimpleName(), rel.localField());
                    continue;
                }

                // 校验远程表元数据
                TableInfo rInfo = LOCAL_TABLE_INFO_MIRROR.computeIfAbsent(rel.targetEntity(), TableInfoFactory::ofEntityClass);
                if (rInfo == null) continue;

                // 确定关联键（默认主键）
                String rLinkColName = StrUtil.isNotBlank(rel.remoteFieldLink()) ?
                        rInfo.getColumnByProperty(rel.remoteFieldLink()) :
                        rInfo.getPrimaryKeyList().getFirst().getColumn();

                QueryColumn rLinkCol = new QueryColumn(rInfo.getTableName(), rLinkColName);
                QueryColumn rTargetCol = new QueryColumn(rInfo.getTableName(), rInfo.getColumnByProperty(rel.remoteField()));

                // 注册底层绑定
                next.bind(localCol, rLinkCol, rTargetCol,
                        (vo, val) -> SystemMetaObject.forObject(vo).setValue(field.getName(), val),
                        rel.matchType(), field.getName(), rel.targetEntity());
            }
        }
        return next;
    }

    /**
     * <h3>强类型 Lambda 绑定 (全链路方法引用)</h3>
     * <p>
     * 提供完全类型安全的绑定方式，支持 1:1 和 1:N 自动适配。
     * </p>
     *
     * @param <T> 目标字段类型
     * @param <RE> 远程实体类型
     * @param localGetter 本地主表的外键 Getter (e.g., {@code User::getDeptId})
     * @param remoteLinkGetter 远程表的关联键 Getter (e.g., {@code Dept::getId})
     * @param remoteTargetGetter 远程表的目标值 Getter (e.g., {@code Dept::getName})
     * @param binder 结果回写 Setter (e.g., {@code UserVO::setDeptName})
     * @param filterType 反向查询过滤类型
     * @return 当前实例
     */
    public <T, RE> FlexSmartQuery<E, R> bind(
            LambdaGetter<E> localGetter,
            LambdaGetter<RE> remoteLinkGetter,
            LambdaGetter<RE> remoteTargetGetter,
            BiConsumer<R, T> binder,
            MatchType filterType
    ) {
        // 1. 解析本地列
        String localProp = LambdaUtil.getFieldName(localGetter);
        QueryColumn localCol = tableInfo.getQueryColumnByProperty(localProp);

        // 2. 解析远程关联列
        @SuppressWarnings("unchecked")
        Class<RE> remoteEntity = (Class<RE>) LambdaUtil.getImplClass(remoteLinkGetter);
        TableInfo rTable = LOCAL_TABLE_INFO_MIRROR.computeIfAbsent(remoteEntity, TableInfoFactory::ofEntityClass);
        String linkColName = rTable.getColumnByProperty(LambdaUtil.getFieldName(remoteLinkGetter));
        QueryColumn remoteLinkCol = new QueryColumn(rTable.getTableName(), linkColName);

        // 3. 转发调用
        return this.bind(localCol, remoteLinkCol, remoteTargetGetter, binder, filterType);
    }

    /**
     * <h3>混合类型绑定 (QueryColumn + Lambda)</h3>
     * <p>
     * 允许混合使用 Column 对象和 Lambda，通常用于复杂的动态列名场景。
     * </p>
     */
    public <T, RE> FlexSmartQuery<E, R> bind(
            QueryColumn localCol,
            QueryColumn remoteLinkCol,
            LambdaGetter<RE> remoteTarget,
            BiConsumer<R, T> binder,
            MatchType filterType
    ) {
        String remoteProp = LambdaUtil.getFieldName(remoteTarget);
        @SuppressWarnings("unchecked")
        Class<RE> remoteEntity = (Class<RE>) LambdaUtil.getImplClass(remoteTarget);
        TableInfo rTable = LOCAL_TABLE_INFO_MIRROR.computeIfAbsent(remoteEntity, TableInfoFactory::ofEntityClass);
        String colName = rTable.getColumnByProperty(remoteProp);

        QueryColumn rTargetCol = new QueryColumn(rTable.getTableName(), colName);
        return this.bind(localCol, remoteLinkCol, rTargetCol, (BiConsumer<Object, Object>) binder, filterType, remoteProp, remoteEntity);
    }

    /**
     * <h3>核心绑定逻辑实现 (The Core Binder)</h3>
     * <p>
     * 这是所有 {@code bind} 方法的最终归宿。它负责两件核心事务：
     * <ol>
     * <li><b>反向过滤注册：</b> 如果 {@code filterType} 不为空，则注册一个 DTO 拦截规则，用于生成 {@code WHERE id IN (SELECT ...)} 子查询。</li>
     * <li><b>回填任务注册：</b> 创建 {@code ExtractionTask} 并放入任务队列，等待查询结果返回后执行。</li>
     * </ol>
     * </p>
     */
    private FlexSmartQuery<E, R> bind(
            QueryColumn localCol,
            QueryColumn remoteLinkCol,
            QueryColumn remoteTargetCol,
            BiConsumer<Object, Object> binder,
            MatchType filterType,
            String dtoPropName,
            Class<?> remoteEntityClass
    ) {
        String localProp = FlexTableMetaCache.getPropertyName(this.entityClass, localCol.getName());
        String tableName = remoteTargetCol.getTable().getName();
        String targetColName = remoteTargetCol.getName();
        String linkColName = remoteLinkCol.getName();

        // --- 逻辑分支 A: 反向搜索过滤 ---
        if (filterType != null && filterType != MatchType.CUSTOM) {
            overrides.put(dtoPropName, new Rule(null, MatchType.CUSTOM, (dummy, val) -> {
                // 1. 构建子查询：在远程表中查找符合条件的记录 ID
                QueryWrapper sub = QueryWrapper.create()
                        .select(linkColName).from(tableName)
                        .where(buildBaseCondition(remoteTargetCol, val, filterType));

                // 2. 执行物理查询获取 ID 列表
                List<Object> hitIds = Mappers.ofEntityClass(remoteEntityClass).selectObjectListByQuery(sub);

                // 3. 生成主表过滤条件：
                // 如果子表没有匹配项，则主表也应该查不到数据 -> 生成 1=2 短路条件
                // 如果有匹配项 -> 生成 local_col IN (ids)
                return CollUtil.isEmpty(hitIds) ? makeShortCircuitCondition() : localCol.in(hitIds);
            }));
        }

        // --- 逻辑分支 B: 结果集回填注册 ---
        if (binder != null) {
            ExtractionTask task = new ExtractionTask();
            task.tableName = tableName;
            task.linkCol = linkColName;
            task.targetCol = targetColName;
            task.localProp = localProp;
            task.binder = binder;
            task.remoteEntityClass = remoteEntityClass;

            // 分析目标属性类型，决定是否启用复杂对象处理
            PropertyDescriptor pd = BeanUtil.getPropertyDescriptor(remoteEntityClass, dtoPropName);
            if (pd != null) {
                task.targetType = pd.getPropertyType();
                // 判定标准：如果是集合，或者不是基本类型/String/Date等，则认为是复杂对象
                task.isComplex = Collection.class.isAssignableFrom(task.targetType) ||
                        (!PRIMITIVE_LIKE_TYPES.contains(task.targetType) && !task.targetType.isPrimitive());
            } else {
                task.targetType = String.class;
            }

            // 使用 "localProp:targetCol" 作为唯一键，防止重复注册
            hydrationTasks.put(localProp + ":" + targetColName, task);
        }
        return this;
    }

    /* ==============================================================================
     * SECTION 8: 语义推导解析引擎 (Semantic Reconstruction)
     * ------------------------------------------------------------------------------
     * 负责将 DTO 的属性值“翻译”成 SQL 的 WHERE 子句。
     * ============================================================================== */

    /**
     * <h3>语义自动化构建器</h3>
     * <p>
     * 遍历 DTO 的所有非空属性，根据后缀规则自动生成查询条件。
     * </p>
     *
     * @param queryDto 查询参数对象
     * @param <D> DTO 泛型
     * @return 当前实例
     */
    public <D> FlexSmartQuery<E, R> autoBuild(D queryDto) {
        if (queryDto == null) return this;

        MetaObject meta = SystemMetaObject.forObject(queryDto);
        Set<String> protocolFields = getProtocolFields(queryDto.getClass());

        for (String fieldName : meta.getGetterNames()) {
            // 1. 过滤：黑名单字段、协议字段（分页排序）
            if (blackListFields.contains(fieldName) || protocolFields.contains(fieldName)) continue;

            Object rawValue = meta.getValue(fieldName);
            // 2. 过滤：空值、空串、空集合
            if (!isSafeForQuery(rawValue)) continue;

            // 3. 优先匹配手动 override 规则
            if (overrides.containsKey(fieldName)) {
                applyToWrapper(overrides.get(fieldName).customFn.apply(null, rawValue));
                continue;
            }

            // 4. 执行语义分析 (e.g., ageBegin -> age >= ?)
            SemanticNode node = resolveSemantic(fieldName);
            QueryColumn targetCol = tableInfo.getQueryColumnByProperty(node.property);

            if (targetCol != null) {
                // 5. 安全处理与去重
                Object finalValue = handleLikeSecurity(rawValue, node.type);
                if (isConditionDuplicate(targetCol, finalValue)) continue;

                // 6. 应用条件
                applyToWrapper(buildBaseCondition(targetCol, finalValue, node.type));
            }
        }
        return this;
    }

    /**
     * <h3>语义后缀识别字典</h3>
     * <p>
     * 集中管理后缀与 SQL 操作符的映射关系。
     * </p>
     */
    private SemanticNode resolveSemantic(String name) {
        // Range: >=
        if (name.endsWith("Begin") || name.endsWith("Min") || name.endsWith("Start") || name.endsWith("From")) {
            String suffix = name.endsWith("Begin") ? "Begin" : (name.endsWith("Start") ? "Start" : (name.endsWith("From") ? "From" : "Min"));
            return new SemanticNode(name.substring(0, name.length() - suffix.length()), MatchType.GE);
        }
        // Range: <=
        if (name.endsWith("End") || name.endsWith("Max") || name.endsWith("Finish") || name.endsWith("To")) {
            String suffix = name.endsWith("Finish") ? "Finish" : (name.endsWith("End") ? "End" : (name.endsWith("To") ? "To" : "Max"));
            return new SemanticNode(name.substring(0, name.length() - suffix.length()), MatchType.LE);
        }
        // Fuzzy: LIKE
        if (name.endsWith("Like")) {
            return new SemanticNode(name.substring(0, name.length() - 4), MatchType.LIKE);
        }
        // Collection: IN
        if (name.endsWith("List") || name.endsWith("Set") || name.endsWith("In")) {
            String suffix = name.endsWith("List") ? "List" : (name.endsWith("Set") ? "Set" : "In");
            return new SemanticNode(name.substring(0, name.length() - suffix.length()), MatchType.IN);
        }
        // Default: =
        return new SemanticNode(name, MatchType.EQ);
    }

    /* ==============================================================================
     * SECTION 9: 执行与数据清洗引擎 (Execution & Hydration)
     * ------------------------------------------------------------------------------
     * 负责与数据库交互，并执行最为关键的“N+1”消除逻辑。
     * ============================================================================== */

    /**
     * <h3>执行列表查询</h3>
     * <p>
     * 触发 SQL 执行，并自动调用 {@code dispatchHydration} 进行关联数据填充。
     * </p>
     *
     * @return 处理完毕的结果列表
     */
    public List<R> list() {
        applySelectProjection();
        List<E> entities = Mappers.ofEntityClass(entityClass).selectListByQuery(queryWrapper);
        if (entities.isEmpty()) return Collections.emptyList();

        List<R> results = convertToFinalType(entities);
        dispatchHydration(results);
        return results;
    }

    /**
     * <h3>执行对象协议分页查询</h3>
     * <p>
     * 从传入的 Page DTO 对象中提取分页和排序参数，并执行查询。
     * </p>
     */
    public Page<R> page(Object pageObj) {
        if (pageObj == null) return page(1, 10);

        int current = extractProtocol(pageObj, PageNo.class, Integer.class).orElse(1);
        int size = Math.min(extractProtocol(pageObj, PageSize.class, Integer.class).orElse(10), SYSTEM_MAX_PAGE_SIZE);

        handleSortLogic(pageObj, SystemMetaObject.forObject(pageObj));

        return page(current, size);
    }

    /**
     * <h3>执行标准分页查询</h3>
     */
    public Page<R> page(int num, int size) {
        applySelectProjection();
        Page<E> page = Mappers.ofEntityClass(entityClass).paginate(Page.of(num, size), queryWrapper);

        List<R> targets = convertToFinalType(page.getRecords());
        dispatchHydration(targets);

        return new Page<>(targets, page.getPageNumber(), page.getPageSize(), page.getTotalRow());
    }

    /**
     * <h3>智能数据回填分发器 (The Hydrator)</h3>
     * <p>
     * 这是消除 "N+1" 问题的核心算法实现。
     * 它的工作流程如下：
     * <ol>
     * <li><b>遍历任务：</b> 依次处理每一个注册的 {@code ExtractionTask}。</li>
     * <li><b>ID 汇聚：</b> 遍历当前页的所有结果对象 (targets)，提取出非空的关联 ID，存入 Set 去重。</li>
     * <li><b>批量抓取：</b> 调用 {@code fetchBatch}，通过 {@code SELECT .. WHERE id IN (..)} 一次性获取所有关联数据。</li>
     * <li><b>内存映射：</b> 将抓取到的数据构建为 {@code Map<ID, List<Value>>} 索引。</li>
     * <li><b>结果注入：</b> 再次遍历结果对象，根据 ID 从 Map 中取值，并调用 binder 回写。</li>
     * </ol>
     * </p>
     * <b>性能提示：</b> 全程在内存中操作，仅产生 O(K) 次数据库查询（K 为关联表数量），而非 O(N) 次。
     *
     * @param targets 待填充的目标结果集
     */
    private void dispatchHydration(List<?> targets) {
        if (targets.isEmpty() || hydrationTasks.isEmpty()) return;

        for (var task : hydrationTasks.values()) {
            // Step 1: 提取所有需要的 Foreign Keys
            Set<Object> ids = targets.stream()
                    .map(t -> SystemMetaObject.forObject(t).getValue(task.localProp))
                    .filter(Objects::nonNull).collect(Collectors.toSet());

            if (ids.isEmpty()) continue;

            // Step 2: 执行批量物理查询 (获取 Map<Key, List<Value>>)
            Map<String, List<String>> dataCache = fetchBatch(task, new ArrayList<>(ids));

            // Step 3: 回填分发
            for (Object obj : targets) {
                Object localIdValue = SystemMetaObject.forObject(obj).getValue(task.localProp);
                if (localIdValue == null) continue;

                List<String> rawDataList = dataCache.get(String.valueOf(localIdValue));
                if (rawDataList != null && !rawDataList.isEmpty()) {
                    try {
                        // 执行自适应类型转换
                        Object finalValue = performConversion(rawDataList, task);
                        // 调用用户定义的 Setter
                        task.binder.accept(obj, finalValue);
                    } catch (Exception e) {
                        log.error("❌ [Hydration] 赋值阶段严重错误：属性={}, 目标类型={}",
                                task.localProp, task.targetType.getSimpleName(), e);
                    }
                }
            }
        }
    }

    /**
     * <h3>批量数据抓取器</h3>
     * <p>
     * 负责生成并执行 {@code WHERE IN} 查询。
     * 支持分片执行以绕过数据库对 SQL 长度或参数个数的限制。
     * </p>
     *
     * @param task 回填任务元数据
     * @param ids 待查询的 ID 列表
     * @return 关联 ID 到目标值列表的映射
     */
    private Map<String, List<String>> fetchBatch(ExtractionTask task, List<Object> ids) {
        Map<String, List<String>> resultMap = new HashMap<>();

        // 分片循环：每次处理 HYDRATION_BATCH_SIZE (500) 个 ID
        for (int i = 0; i < ids.size(); i += HYDRATION_BATCH_SIZE) {
            int end = Math.min(i + HYDRATION_BATCH_SIZE, ids.size());

            // 安全转义：虽然是内部 ID，但为了防止极端情况，仍进行 SQL 转义
            String inValues = ids.subList(i, end).stream()
                    .map(this::sqlEscape)
                    .collect(Collectors.joining(","));

            // 动态构建两列查询 SQL：SELECT key, value FROM table WHERE key IN (...)
            String sql = String.format("SELECT %s as k, %s as v FROM %s WHERE %s IN (%s)",
                    task.linkCol, task.targetCol, task.tableName, task.linkCol, inValues);

            // 执行并解析结果
            List<Map<String, Object>> rows = searchMapper.executeDynamicUnionQuery(sql);
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    String k = String.valueOf(row.get("k"));
                    String v = String.valueOf(row.get("v"));
                    // 支持 1:N 聚合：同一个 Key 可能对应多个 Value
                    resultMap.computeIfAbsent(k, key -> new ArrayList<>()).add(v);
                }
            }
        }
        return resultMap;
    }

    /**
     * <h3>自适应类型转换器 (Adaptive Converter)</h3>
     * <p>
     * 根据目标 VO 字段的类型，决定如何处理从数据库抓取到的原始字符串列表。
     * </p>
     * <b>策略逻辑：</b>
     * <ul>
     * <li><b>集合模式 (1:N)：</b> 如果目标类型是 {@code List/Set}，则将所有结果转换为对应泛型并返回集合。</li>
     * <li><b>单值模式 (1:1)：</b> 如果目标类型是普通对象，则只取列表中的第一个值 (First-Win)。</li>
     * <li><b>JSON 模式：</b> 如果标记为 {@code isComplex}，则调用 Jackson 进行反序列化。</li>
     * </ul>
     *
     * @param rawList 原始字符串列表
     * @param task 任务上下文
     * @return 转换后的 Java 对象
     * @throws JsonProcessingException JSON 解析失败
     */
    private Object performConversion(List<String> rawList, ExtractionTask task) throws JsonProcessingException {
        if (CollUtil.isEmpty(rawList)) return null;

        // --- 策略分支 A: 目标是集合 (Collection) ---
        if (Collection.class.isAssignableFrom(task.targetType)) {
            return rawList.stream()
                    .map(raw -> {
                        try {
                            // 递归检查：如果集合泛型是复杂对象，也需要 JSON 解析
                            if (!PRIMITIVE_LIKE_TYPES.contains(task.targetType)) {
                                return globalObjectMapper.readValue(raw, Object.class);
                            }
                            return raw;
                        } catch (Exception e) { return raw; } // 容错处理
                    }).collect(Collectors.toList());
        }

        // --- 策略分支 B: 目标是单值 (Single Value) ---
        String firstRaw = rawList.get(0);
        if (StrUtil.isBlank(firstRaw) || "null".equalsIgnoreCase(firstRaw)) return null;

        // 简单类型：使用 Hutool 转换
        if (!task.isComplex) {
            return BeanUtil.toBean(firstRaw, task.targetType);
        }
        // 复杂类型：使用 Jackson 反序列化
        return globalObjectMapper.readValue(firstRaw, task.targetType);
    }

    /* ==============================================================================
     * SECTION 10: 防御性辅助与反射工具集 (Defensive & Reflection Helpers)
     * ------------------------------------------------------------------------------
     * 包含安全过滤、类型推断、SQL 生成等底层工具方法。
     * ============================================================================== */

    /**
     * <h3>模糊查询安全过滤器</h3>
     * <p>
     * 对外部输入的模糊查询字符串进行清洗。
     * </p>
     * <b>处理逻辑：</b>
     * <ul>
     * <li>去除非 String 类型。</li>
     * <li>去除首尾空格。</li>
     * <li>检测并拦截仅包含 {@code %} 的全表扫描攻击。</li>
     * <li>强制截断超过 64 字符的输入。</li>
     * </ul>
     */
    private Object handleLikeSecurity(Object val, MatchType t) {
        if (!(val instanceof String s) || !isLikeMatch(t)) return val;
        String trimmed = s.trim();
        // 防御全表扫描攻击 (e.g. "%%%")
        if (trimmed.matches("^%+$")) return "";
        // 防御慢查询攻击
        return trimmed.length() > MAX_LIKE_INPUT_LENGTH ? trimmed.substring(0, MAX_LIKE_INPUT_LENGTH) : trimmed;
    }

    /**
     * 判断当前操作符是否属于模糊查询范畴。
     */
    private boolean isLikeMatch(MatchType t) {
        return t == MatchType.LIKE || t == MatchType.LEFT_LIKE || t == MatchType.RIGHT_LIKE;
    }

    /**
     * <h3>条件去重检查</h3>
     * <p>
     * 防止 DTO 中通过不同逻辑对同一列添加了重复的条件，导致 SQL 冗余或冲突。
     * </p>
     */
    private boolean isConditionDuplicate(QueryColumn col, Object val) {
        List<Object> history = conditionRecord.computeIfAbsent(col, k -> new ArrayList<>());
        if (history.contains(val)) return true;
        history.add(val);
        return false;
    }

    /**
     * <h3>原子条件构建工厂</h3>
     * <p>
     * 将业务层的 MatchType 枚举翻译为 MyBatis-Flex 的 QueryCondition 对象。
     * </p>
     */
    private QueryCondition buildBaseCondition(QueryColumn col, Object val, MatchType t) {
        // 特殊处理：集合查询如果传入空集合，应生成短路条件而非报错
        if ((t == MatchType.IN || t == MatchType.NOT_IN) && ObjUtil.isEmpty(val)) {
            return makeShortCircuitCondition();
        }
        return switch (t) {
            case EQ -> col.eq(val);
            case NE -> col.ne(val);
            case GE -> col.ge(val);
            case GT -> col.gt(val);
            case LE -> col.le(val);
            case LT -> col.lt(val);
            case LIKE -> col.like(val);
            case IN -> (val instanceof Collection<?> c) ? col.in(c) : col.eq(val);
            case NOT_IN -> (val instanceof Collection<?> c) ? col.notIn(c) : col.ne(val);
            default -> null;
        };
    }

    /**
     * <h3>SQL 短路条件生成器</h3>
     * <p>
     * 生成一个永远为假的条件 ({@code 1 = 2})。
     * 用于在 {@code IN} 查询参数为空时，确保 SQL 不会查出全表数据，而是直接返回空结果。
     * </p>
     */
    private QueryCondition makeShortCircuitCondition() {
        return QueryCondition.create(QueryMethods.raw("1").getColumn(), "=", 2);
    }

    /**
     * <h3>简易 SQL 转义工具</h3>
     * <p>
     * 仅用于处理内部生成的 ID 列表。虽然 ID 通常是安全的，但为了代码健壮性，
     * 仍对单引号进行了转义处理。
     * </p>
     */
    private String sqlEscape(Object v) {
        if (v instanceof Number) return String.valueOf(v);
        return "'" + String.valueOf(v).replace("'", "''") + "'";
    }

    /**
     * <h3>应用 SELECT 投影</h3>
     * <p>
     * 将收集到的 {@code selectColumns} 应用到 QueryWrapper 中。
     * </p>
     */
    private void applySelectProjection() {
        if (!selectColumns.isEmpty()) queryWrapper.select(selectColumns.toArray(new QueryColumn[0]));
    }

    /**
     * <h3>应用查询条件</h3>
     * <p>
     * 根据 {@code useOrLogic} 标志位，决定是使用 AND 还是 OR 连接新条件。
     * </p>
     */
    private void applyToWrapper(QueryCondition c) {
        if (c != null) { if (useOrLogic) queryWrapper.or(c); else queryWrapper.and(c); }
    }

    /**
     * <h3>最终结果集转换</h3>
     * <p>
     * 如果结果类型 {@code R} 与实体类型 {@code E} 不同（例如 Entity -> VO），
     * 则使用 BeanUtil 进行批量属性拷贝。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private List<R> convertToFinalType(List<E> es) {
        return resultClass == entityClass ? (List<R>) es : es.stream().map(e -> BeanUtil.copyProperties(e, resultClass)).toList();
    }

    /**
     * <h3>协议参数反射提取器</h3>
     * <p>
     * 从任意对象中，查找被 {@code @PageNo}, {@code @SortField} 等注解标记的字段值。
     * </p>
     */
    private <T> Optional<T> extractProtocol(Object o, Class<? extends Annotation> a, Class<T> t) {
        for (Field f : o.getClass().getDeclaredFields()) {
            if (f.isAnnotationPresent(a)) {
                Object v = SystemMetaObject.forObject(o).getValue(f.getName());
                if (v != null && t.isAssignableFrom(v.getClass())) return Optional.of((T) v);
            }
        }
        return Optional.empty();
    }

    /**
     * <h3>分页排序逻辑处理器</h3>
     * <p>
     * 解析对象中的排序协议注解，并将其应用到 QueryWrapper 的 orderBy 子句中。
     * </p>
     */
    private void handleSortLogic(Object dto, MetaObject meta) {
        String f = extractProtocol(dto, SortField.class, String.class).orElse(null);
        if (StrUtil.isNotBlank(f)) {
            String o = extractProtocol(dto, SortOrder.class, String.class).orElse("asc");
            QueryColumn col = tableInfo.getQueryColumnByProperty(f);
            if (col != null) {
                if (o.toLowerCase().contains("desc")) queryWrapper.orderBy(col.desc());
                else queryWrapper.orderBy(col.asc());
            }
        }
    }

    /**
     * <h3>获取协议字段集合</h3>
     * <p>
     * 扫描类定义，返回所有被协议注解标记的字段名集合。
     * 这些字段在 {@code autoBuild} 过程中会被自动跳过。
     * </p>
     */
    private Set<String> getProtocolFields(Class<?> c) {
        Set<String> s = new HashSet<>();
        for (Field f : c.getDeclaredFields()) {
            if (f.isAnnotationPresent(PageNo.class) || f.isAnnotationPresent(PageSize.class) ||
                    f.isAnnotationPresent(SortField.class) || f.isAnnotationPresent(SortOrder.class)) s.add(f.getName());
        }
        return s;
    }

    /**
     * <h3>语义节点</h3>
     * <p>
     * 简单的内部数据载体，用于在 {@code resolveSemantic} 和 {@code autoBuild} 之间传递解析结果。
     * </p>
     */
    private record SemanticNode(String property, MatchType type) {}

    /**
     * <h3>查询值安全性检查</h3>
     * <p>
     * 判断一个值是否有效，是否值得生成 SQL 条件。
     * 排除 null、空字符串、空集合。
     * </p>
     */
    private boolean isSafeForQuery(Object v) {
        return v != null && (!(v instanceof String s) || StrUtil.isNotBlank(s)) && (!(v instanceof Collection<?> c) || !c.isEmpty());
    }

    /**
     * <h3>获取当前 SQL (调试用)</h3>
     * <p>
     * 返回 QueryWrapper 目前构建出的 SQL 语句。
     * </p>
     */
    public String toSQL() { return queryWrapper.toSQL(); }

    /**
     * <h3>链式排序：升序</h3>
     */
    public FlexSmartQuery<E, R> orderBy(LambdaGetter<E> g) {
        String n = LambdaUtil.getFieldName(g);
        QueryColumn col = tableInfo.getQueryColumnByProperty(n);
        if (col != null) queryWrapper.orderBy(col.asc());
        return this;
    }

    /**
     * <h3>链式排序：降序</h3>
     */
    public FlexSmartQuery<E, R> orderByDesc(LambdaGetter<E> g) {
        String n = LambdaUtil.getFieldName(g);
        QueryColumn col = tableInfo.getQueryColumnByProperty(n);
        if (col != null) queryWrapper.orderBy(col.desc());
        return this;
    }

    /* ==============================================================================
     * SECTION 11: 架构师手记与维护指南 (Architect's Notes)
     * ------------------------------------------------------------------------------
     * 本组件的设计初衷是解决“开发效率”与“运行性能”之间的矛盾。
     * 在维护本组件时，请务必遵守以下原则：
     * <ol>
     * <li><b>保持无状态：</b> FlexSmartQuery 实例是短生命周期的对象，严禁设计为单例。</li>
     * <li><b>性能优先：</b> 在热点路径（如 dispatchHydration）中，避免使用低效的反射或深拷贝。</li>
     * <li><b>类型安全：</b> 所有新增 API 必须优先考虑 Lambda 支持，拒绝字符串参数。</li>
     * </ol>
     * ============================================================================== */
}
