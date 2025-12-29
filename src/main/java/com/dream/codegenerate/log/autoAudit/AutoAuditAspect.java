package com.dream.codegenerate.log.autoAudit;

import com.dream.codegenerate.log.OperationContext;
import com.dream.codegenerate.mapper.UniversalSearchMapper;
import com.mybatisflex.core.mybatis.Mappers;
import com.mybatisflex.core.table.TableInfo;
import com.mybatisflex.core.table.TableInfoFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;
import java.util.*;

/**
 * 自动审计切面 (AutoAuditAspect)
 * <p>
 * 职责：
 * 1. 拦截 @AuditLog 方法，提取数据变更前后的快照。
 * 2. 事务提交后，异步执行 diff 对比。
 * 3. [修复] 解决异步线程丢失 ScopedValue 上下文的问题。
 * </p>
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(2) // [关键] 运行在 OperationLogAspect 内部，确保能获取到 ScopedValue
public class AutoAuditAspect {

    private final ThreadPoolTaskExecutor taskExecutor;

    @Around("@annotation(auditLog)")
    public Object doAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        // 1. 识别目标实体
        Class<?>[] entities = resolveEntities(auditLog);
        Object[] args = joinPoint.getArgs();
        List<AuditTarget> targets = identify(entities, args);

        // 2. 抓取旧值快照 (Before Execution)
        Map<Class<?>, Object> oldSnapshots = fetch(targets);

        // 3. [核心修复] 捕获当前主线程的上下文快照
        // 此时我们处于 OperationLogAspect 的 Scope 内部，可以拿到 operatorId 等信息
        OperationContext.OperationInfo contextSnapshot = OperationContext.get();

        long start = System.currentTimeMillis();

        // 4. 执行业务逻辑
        Object result = joinPoint.proceed();

        long cost = System.currentTimeMillis() - start;

        // 5. 定义审计任务
        Runnable auditTask = () -> {
            // [核心修复] 在异步线程中重新绑定 ScopedValue
            // 如果不这样做，SmartAuditUpdater 里的 OperationContext.get() 会返回默认的 Guest 上下文
            try {
                ScopedValue.where(OperationContext.getScopedValue(), contextSnapshot)
                        .run(() -> processAudit(auditLog, targets, oldSnapshots, result, cost));
            } catch (Exception e) {
                log.error("Async Audit Error", e);
            }
        };

        // 6. 注册事务回调 (确保数据落库后再审计)
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutor.execute(auditTask);
                }
            });
        } else {
            // 无事务环境直接异步执行
            taskExecutor.execute(auditTask);
        }

        return result;
    }

    private void processAudit(AuditLog al, List<AuditTarget> ts, Map<Class<?>, Object> olds, Object res, long cost) {
        List<SmartAuditUpdater<?, ?>> updaters = new ArrayList<>();

        for (var t : ts) {
            Object oldVal = olds.get(t.clazz());
            // 如果返回值是 Entity 类型，优先使用返回值作为新值(可能包含数据库生成的值)，否则使用参数作为新值
            Object newVal = t.clazz().isInstance(res) ? res : t.input();

            // 构建对比器
            updaters.add(
                    SmartAuditUpdater.copy(oldVal, newVal)
                            .partial(al.partial())
                            .skipNullFields(al.skipNull())
                            .includeSystemFields(al.systemFields())
            );
        }
        // 执行批量对比 (现在会自动聚合日志)
        SmartAuditUpdater.executeBatch(al.module(), al.action(), updaters, cost);
    }

    private Map<Class<?>, Object> fetch(List<AuditTarget> targets) {
        if (targets.isEmpty()) return Collections.emptyMap();
        Map<Class<?>, Object> res = new HashMap<>();
        for (var t : targets) {
            // 使用 MyBatis-Flex 查询旧数据
            Object entity = Mappers.ofEntityClass(t.clazz()).selectOneById(t.id());
            if (entity != null) res.put(t.clazz(), entity);
        }
        return res;
    }

    private List<AuditTarget> identify(Class<?>[] classes, Object[] args) {
        List<AuditTarget> list = new ArrayList<>();
        for (Class<?> c : classes) {
            TableInfo info = TableInfoFactory.ofEntityClass(c);
            if (info == null) continue;
            for (Object arg : args) {
                if (c.isInstance(arg)) {
                    try {
                        // 反射获取 ID
                        Object id = org.apache.ibatis.reflection.SystemMetaObject.forObject(arg)
                                .getValue(info.getPrimaryKeyList().getFirst().getProperty());
                        if (id instanceof Serializable s) list.add(new AuditTarget(c, s, arg));
                    } catch (Exception ignored) {}
                    break;
                }
            }
        }
        return list;
    }

    private Class<?>[] resolveEntities(AuditLog al) {
        if (al.entities().length > 0) return al.entities();
        return al.entity() != Object.class ? new Class<?>[]{al.entity()} : new Class<?>[0];
    }

    private record AuditTarget(Class<?> clazz, Serializable id, Object input) {}
}
