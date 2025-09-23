package com.dream.codegenerate.ai.model.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 当 AI 准备调用 writeFile 工具时，用于向前端流式传输文件内容的事件
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class StreamingToolMessage extends StreamMessage {

    private String id;

    private String name;

    private String arguments;

    public StreamingToolMessage(ToolExecutionRequest toolExecutionRequest) {
        // 使用一个固定的、通用的事件类型
        super(StreamMessageTypeEnum.TOOL_STREAM.getValue());
        this.id = toolExecutionRequest.id();
        this.name = toolExecutionRequest.name();
        this.arguments = toolExecutionRequest.arguments();
    }
}
