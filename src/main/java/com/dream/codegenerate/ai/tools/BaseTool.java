package com.dream.codegenerate.ai.tools;

import cn.hutool.json.JSONObject;
import com.dream.codegenerate.ai.tools.context.SessionContext;
import com.dream.codegenerate.ai.tools.context.SessionContextManager;
import jakarta.annotation.Resource;

/**
 * 工具基类
 * 定义所有工具的通用接口
 */
public abstract class BaseTool {



    private SessionContextManager contextManager;

    /**
     * 为子类提供一个受保护的、便捷的方法来获取当前会话的上下文。
     *
     * @param appId 当前会话的 ID (通常由 @ToolMemoryId 提供)
     * @return 当前会话的上下文对象
     */
    protected SessionContext getContext(Long appId) {
        if (contextManager == null) {
            // 提供一个友好的错误提示，防止 contextManager 未被注入的极端情况
            throw new IllegalStateException("SessionContextManager has not been injected. " +
                    "Ensure that all tool subclasses are managed Spring Beans (e.g., with @Component).");
        }
        return contextManager.getContext(appId);
    }

    /**
     * 通过 Setter 方法进行注入。
     * Spring 在创建任何 BaseTool 的子类 Bean 时，都会自动调用此方法。
     *
     * @param contextManager 由 Spring 容器提供的 SessionContextManager Bean
     */
    @Resource
    public void setContextManager(SessionContextManager contextManager) {
        this.contextManager = contextManager;
    }

    /**
     * 获取工具的英文名称（对应方法名）
     *
     * @return 工具英文名称
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     *
     * @return 工具请求显示内容
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（保存到数据库）
     *
     * @param arguments 工具执行参数
     * @return 格式化的工具执行结果
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);
}
