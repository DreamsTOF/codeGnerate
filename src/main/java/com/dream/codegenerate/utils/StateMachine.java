package com.dream.codegenerate.utils;

import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * StateMachine - 声明式业务状态机引擎
 * <p>
 * 解决逻辑：
 * 1. 消除 Service 层复杂的 switch-case 状态转换判断。
 * 2. 集中化管理状态流转路径（From -> On -> To）。
 * 3. 声明式定义前置条件 (when) 与后置动作 (call)。
 * </p>
 *
 * @param <S> Status 类型 (通常为枚举)
 * @param <E> Event 类型 (通常为枚举)
 * @param <C> Context 类型 (业务上下文，包含数据和 Service 引用)
 */
public class StateMachine<S, E, C> {

    // 状态转换表：Map<FromStatus, Map<Event, List<Transition>>>
    private final Map<S, Map<E, List<Transition<S, E, C>>>> transitionMap = new HashMap<>();

    private StateMachine() {}

    /**
     * 创建状态机配置构建器
     */
    public static <S, E, C> Builder<S, E, C> builder() {
        return new Builder<>();
    }

    /**
     * 触发事件，驱动状态机
     *
     * @param currentState 当前状态
     * @param event        触发事件
     * @param context      业务上下文
     * @return 转换后的新状态
     */
    public S fire(S currentState, E event, C context) {
        Map<E, List<Transition<S, E, C>>> eventMap = transitionMap.get(currentState);

        // 1. 校验当前状态是否支持该事件
        // [修改点] 移除 ThrowUtils 依赖，使用 Asserts.isTrue 或直接判断
        if (eventMap == null || !eventMap.containsKey(event)) {
            throw new BusinessException(ErrorCode.STATUS_ERROR,
                    String.format("当前状态 [%s] 无法处理事件 [%s]", currentState, event));
        }

        List<Transition<S, E, C>> transitions = eventMap.get(event);

        // 2. 查找匹配前置条件的转换规则
        // [优化] 如果没有 condition 的规则，直接匹配，避免 stream 开销 (虽然 stream 很干净，但此处逻辑简单)
        Transition<S, E, C> matchTransition = null;
        for (Transition<S, E, C> t : transitions) {
            if (t.condition == null || t.condition.test(context)) {
                matchTransition = t;
                break;
            }
        }

        if (matchTransition == null) {
            throw new BusinessException(ErrorCode.STATUS_ERROR, "业务前置条件校验未通过，无法执行转换");
        }

        // 3. 执行后置动作
        if (matchTransition.action != null) {
            matchTransition.action.accept(context);
        }

        return matchTransition.toStatus;
    }

    /**
     * 转换规则实体
     */
    private static class Transition<S, E, C> {
        S fromStatus;
        E event;
        S toStatus;
        Predicate<C> condition;
        Consumer<C> action;
    }

    /**
     * 状态机配置构建器
     */
    @NoArgsConstructor
    public static class Builder<S, E, C> {
        private final StateMachine<S, E, C> machine = new StateMachine<>();
        // [优化] 移除 unused field 'currentTransition'

        /**
         * 定义一个新的转换路径
         */
        public TransitionBuilder transition() {
            return new TransitionBuilder(this);
        }

        public StateMachine<S, E, C> build() {
            return machine;
        }

        /**
         * 内部转换构建器
         */
        public class TransitionBuilder {
            private final Builder<S, E, C> parent;
            private final Transition<S, E, C> transition = new Transition<>();

            TransitionBuilder(Builder<S, E, C> parent) {
                this.parent = parent;
            }

            public TransitionBuilder from(S from) {
                transition.fromStatus = from;
                return this;
            }

            public TransitionBuilder on(E event) {
                transition.event = event;
                return this;
            }

            public TransitionBuilder to(S to) {
                transition.toStatus = to;
                return this;
            }

            /**
             * 设置前置校验条件（如：余额是否足够、是否有权限）
             */
            public TransitionBuilder when(Predicate<C> condition) {
                transition.condition = condition;
                return this;
            }

            /**
             * 设置转换成功后的后置动作（如：发送通知、写审计日志）
             */
            public TransitionBuilder call(Consumer<C> action) {
                transition.action = action;
                return this;
            }

            /**
             * 保存当前转换规则并返回父构建器
             */
            public Builder<S, E, C> done() {
                machine.transitionMap
                        .computeIfAbsent(transition.fromStatus, k -> new HashMap<>())
                        .computeIfAbsent(transition.event, k -> new ArrayList<>())
                        .add(transition);
                return parent;
            }
        }
    }
}
