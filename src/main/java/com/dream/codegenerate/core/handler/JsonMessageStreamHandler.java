package com.dream.codegenerate.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dream.codegenerate.ai.model.message.*;
import com.dream.codegenerate.ai.tools.BaseTool;
import com.dream.codegenerate.ai.tools.ToolManager;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.enums.ChatHistoryMessageTypeEnum;
import com.dream.codegenerate.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<ServerSentEvent<String>> handle(Flux<ServerSentEvent<String>> originFlux,
                                          ChatHistoryService chatHistoryService,
                                          long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .filter(event -> StrUtil.isNotEmpty(event.data())) // 过滤空字串
                // 【核心修改】使用 flatMap 来实现事件的追加
                .flatMap(event -> {
                    // 1. 处理事件，该方法会更新历史记录并返回需要作为新事件发送的额外数据
                    String additionalData = processAndGetAdditionalData(event, chatHistoryStringBuilder, seenToolIds);

                    // 2. 如果没有额外数据，则只返回原始事件
                    if (additionalData == null) {
                        return Flux.just(event);
                    }

                    // 3. 如果有额外数据，则创建一个新事件
                    AiResponseMessage aiResponseMessage = new AiResponseMessage(additionalData);
                    ServerSentEvent<String> additionalEvent = ServerSentEvent.<String>builder()
                            .event(StreamMessageTypeEnum.AI_RESPONSE.getValue())
                            .data(JSONUtil.toJsonStr(aiResponseMessage)) // 使用新生成的数据
                            .build();

                    // 4. 返回一个包含【原始事件】和【新追加事件】的流
                    return Flux.just(event, additionalEvent);
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    String aiResponse = chatHistoryStringBuilder.toString();
                    String finalResponse = aiResponse + "\n" + errorMessage;
                    chatHistoryService.addChatMessage(appId, finalResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
//                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 【重构】此方法现在返回需要作为【额外事件】发送的数据。
     * 它的职责是：1. 解析事件数据；2. 将内容追加到 StringBuilder；3. 返回需要追加发送给前端的数据。
     * @return 需要作为新事件发送的 data 字符串，如果不需要发送额外事件则返回 null。
     */
    private String processAndGetAdditionalData(ServerSentEvent<String> chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        try {
            StreamMessage streamMessage = JSONUtil.toBean(chunk.data(), StreamMessage.class);
            StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());

            if (typeEnum == null) return null;

            switch (typeEnum) {
                case AI_RESPONSE: {
                    AiResponseMessage aiMessage = JSONUtil.toBean(chunk.data(), AiResponseMessage.class);
                    chatHistoryStringBuilder.append(aiMessage.getData());
                    // 对于AI响应，原始事件已包含所需信息，无需追加新事件
                    return null;
                }
                case TOOL_REQUEST: {
                    ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk.data(), ToolRequestMessage.class);
                    String toolId = toolRequestMessage.getId();
                    String toolName = toolRequestMessage.getName();

                    if (toolId != null && !seenToolIds.contains(toolId)) {
                        seenToolIds.add(toolId);
                        BaseTool tool = toolManager.getTool(toolName);
                        if (tool != null) {
                            String toolResponse = tool.generateToolRequestResponse();
                            chatHistoryStringBuilder.append(toolResponse);
                            // 返回工具响应，它将作为一个新事件被发送
                            return toolResponse;
                        }
                    }
                    return null;
                }
                case TOOL_EXECUTED:
                case TOOL_STREAM: {
                    ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk.data(), ToolExecutedMessage.class);
                    String toolName = toolExecutedMessage.getName();
                    BaseTool tool = toolManager.getTool(toolName);
                    if (tool != null) {
                        JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                        String result = tool.generateToolExecutedResult(jsonObject);
                        String output = String.format("\n\n%s\n\n", result);
                        chatHistoryStringBuilder.append(output);
                        // 返回工具执行结果，它将作为一个新事件被发送
                        return output;
                    }
                    return null;
                }
                default: {
                    log.warn("未处理的历史记录事件类型: {}", typeEnum);
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("为历史记录解析SSE事件时出错: {}", chunk, e);
            return null;
        }
    }
    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getTool(toolName);
                    // 返回格式化的工具调用信息
                    return tool.generateToolRequestResponse();
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                // 根据工具名称获取工具实例
                String toolName = toolExecutedMessage.getName();
                BaseTool tool = toolManager.getTool(toolName);
                String result = tool.generateToolExecutedResult(jsonObject);
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }


}
