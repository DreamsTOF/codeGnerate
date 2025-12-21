package com.dream.codegenerate.utils.autoAudit;

import com.mybatisflex.core.mybatis.Mappers;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * ==================================================================================
 * 🚀 AutoAuditAspect - 自动数据审计“一枪流”版
 * ==================================================================================
 * 借鉴 FlexSmartQuery 的设计哲学：
 * 1. 协议化：通过 @AuditLog 注解定义审计范围。
 * 2. 自动化：利用 TableInfo 和 Mappers 动态定位数据，无需手写业务代码。
 * 3. 零侵入：在 ScopedValue 作用域内自动完成“旧值->执行->新值->对比”全链路。
 * ==================================================================================
 */
@Aspect
@Component
@Slf4j
public class AutoAuditAspect {

    /**
     * 核心审计环绕增强
     */
    @Around("@annotation(auditLog)")
    public Object doAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object[] args = joinPoint.getArgs();

        // 1. 安全校验：无参方法或未指定实体的注解直接跳过
        if (args.length == 0 || auditLog.entity() == Object.class) {
            return joinPoint.proceed();
        }

        Class<?> entityClass = auditLog.entity();
        TableInfo tableInfo = TableInfoFactory.ofEntityClass(entityClass);
        if (tableInfo == null) return joinPoint.proceed();

        // 2. 智能主键定位 (借鉴一枪流的元数据意识)
        Serializable id = resolveId(args[0], tableInfo);
        if (id == null) return joinPoint.proceed();

        // 3. 极简 Mapper 获取 (参考 FlexSmartQuery 使用 Mappers 工具类)
        var mapper = Mappers.ofEntityClass(entityClass);

        // 4. 执行“数据快照”对比流程
        // ---------------------------------------------------------
        // A. 抓取旧值
        Object oldEntity = mapper.selectOneById(id);

        // B. 执行业务逻辑 (此时仍在 OperationLogAspect 的 ScopedValue 作用域内)
        Object result = joinPoint.proceed();

        // C. 抓取新值
        Object newEntity = mapper.selectOneById(id);

        // D. 触发一枪流对比引擎
        if (oldEntity != null && newEntity != null) {
            SmartAuditUpdater.copy(oldEntity, newEntity)
                    .withAction(auditLog.action())
                    .execute();
        }
        // ---------------------------------------------------------

        return result;
    }

    /**
     * 智能解析 ID
     * 逻辑：如果参数是基本类型则视为 ID，如果是对象则利用 TableInfo 获取属性名并提取值
     */
    private Serializable resolveId(Object arg, TableInfo tableInfo) {
        if (arg == null) return null;

        // 场景 1: 参数本身就是 ID (Number 或 String)
        if ((arg instanceof Number || arg instanceof String)) {
            return (Serializable)arg;
        }

        // 场景 2: 参数是实体对象，通过元数据主键列表提取属性名
        try {
            var pks = tableInfo.getPrimaryKeyList();
            if (pks != null && !pks.isEmpty()) {
                // 1. 获取主键字段对应的属性名 (如 "id")
                String propertyName = pks.getFirst().getProperty();

                // 2. 借鉴 FlexSmartQuery 方案：使用 MyBatis 的 SystemMetaObject 提取值
                // 这种方式比调用 IdInfo.getValue() 更具兼容性
                Object pkVal = SystemMetaObject.forObject(arg).getValue(propertyName);

                if (pkVal instanceof Serializable s) return s;
            }
        } catch (Exception e) {
            log.debug("无法从参数中提取主键值: {}", e.getMessage());
        }

        return null;
    }
}
