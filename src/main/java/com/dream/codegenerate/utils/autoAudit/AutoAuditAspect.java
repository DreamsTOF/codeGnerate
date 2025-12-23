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
 * AutoAuditAspect - 自动审计“智能降级”版
 * * 性能哲学：
 * 1. 两次 IO 基准：默认 1 Select (旧值) + 1 Update。
 * 2. PG RETURNING 适配：如果方法返回了实体类，优先将其视为“全量新值”以获得最高审计精度。
 * 3. 智能降级：若无返回值，则将入参视为“增量新值”，开启 partial 模式对比，不增加额外 IO。
 */
@Aspect
@Component
@Slf4j
public class AutoAuditAspect {

    @Around("@annotation(auditLog)")
    public Object doAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        Object[] args = joinPoint.getArgs();

        // 基本校验
        if (args.length == 0 || auditLog.entity() == Object.class) {
            return joinPoint.proceed();
        }

        Class<?> entityClass = auditLog.entity();
        TableInfo tableInfo = TableInfoFactory.ofEntityClass(entityClass);
        if (tableInfo == null) return joinPoint.proceed();

        // 1. 获取 ID 与入参快照
        Object inputParam = args[0];
        Serializable id = resolveId(inputParam, tableInfo);
        if (id == null) return joinPoint.proceed();

        // 2. 抓取旧值快照 (第 1 次 IO)
        var mapper = Mappers.ofEntityClass(entityClass);
        Object oldEntity = mapper.selectOneById(id);

        // 3. 执行业务更新逻辑 (第 2 次 IO: 数据库 Update)
        Object result = joinPoint.proceed();

        // 4. 智能识别新值来源 (实现 RETURNING 适配与降级)
        if (oldEntity != null) {
            Object newSnapshot;
            boolean isPartialMode;

            // 智能判断：如果返回值就是实体类型 (说明触发了 PG RETURNING 协议或 Service 返回了完整新对象)
            if (entityClass.isInstance(result)) {
                newSnapshot = result;
                isPartialMode = false; // 全量对比，精度最高
            } else {
                // 降级：将入参视为新值来源
                newSnapshot = inputParam;
                isPartialMode = true; // 仅对比入参中被修改的字段
            }

            // 5. 触发对比引擎
            SmartAuditUpdater.copy(oldEntity, newSnapshot)
                    .partial(isPartialMode)
                    .withAction(auditLog.action())
                    .execute();
        }

        return result;
    }

    /**
     * 智能解析 ID
     */
    private Serializable resolveId(Object arg, TableInfo tableInfo) {
        if (arg == null) return null;
        if ((arg instanceof Number || arg instanceof String)) return (Serializable)arg;
        try {
            var pks = tableInfo.getPrimaryKeyList();
            if (pks != null && !pks.isEmpty()) {
                String propertyName = pks.getFirst().getProperty();
                Object pkVal = SystemMetaObject.forObject(arg).getValue(propertyName);
                if (pkVal instanceof Serializable s) return s;
            }
        } catch (Exception e) {
            log.debug("无法提取主键: {}", e.getMessage());
        }
        return null;
    }
}
