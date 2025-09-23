package com.dream.codegenerate.core.context;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话上下文管理器
 * 统一管理所有 appId 的上下文状态
 */
@Service
public class SessionContextManager {

    private final Map<Long, SessionContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 获取或创建一个新的会话上下文
     * @param appId 会话 ID
     * @return 会话上下文对象
     */
    public SessionContext getContext(Long appId) {
        return contextMap.computeIfAbsent(appId, k -> new SessionContext());
    }

    /**
     * 移除一个会话的上下文（例如，当对话结束或超时）
     * @param appId 会话 ID
     */
    public void removeContext(Long appId) {
        contextMap.remove(appId);
    }
}
