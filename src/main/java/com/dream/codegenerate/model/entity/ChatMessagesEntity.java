package com.dream.codegenerate.model.entity;

import com.dream.codegenerate.model.enums.MessageTypeEnum;
import com.dream.codegenerate.utils.JsonbStringTypeHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.io.Serial;

import com.mybatisflex.core.handler.JacksonTypeHandler;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.pgvector.PGvector;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.postgresql.util.PGobject;

/**
 *  实体类。
 *
 * @author dream
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(dataSource="pg",value = "chat_messages")
public class ChatMessagesEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String memoryId;


    private MessageTypeEnum messageType;

    @Column(typeHandler = JsonbStringTypeHandler.class)
    private String contents;

    private String text;

    @Column(typeHandler = JsonbStringTypeHandler.class)
    private String toolExecutionRequests;

    private String toolCallId;

    private String toolName;

    private PGvector embedding;

    private OffsetDateTime createdAt;


    /**
     * 静态工厂方法：从 langchain4j 的 ChatMessage DTO 创建数据库实体。
     * 在此方法中执行序列化操作。
     */
    public static ChatMessagesEntity from(String memoryId, ChatMessage message, ObjectMapper objectMapper) {
        ChatMessagesEntityBuilder builder = ChatMessagesEntity.builder().memoryId(memoryId);

        try {
            switch (message) {
                case UserMessage userMessage -> {
                    builder.messageType(MessageTypeEnum.USER);
                    builder.contents(objectMapper.writeValueAsString(userMessage.contents()));
                }
                case AiMessage aiMessage -> {
                    builder.messageType(MessageTypeEnum.AI);
                    builder.text(aiMessage.text());
                    if (aiMessage.hasToolExecutionRequests()) {
                        builder.toolExecutionRequests(objectMapper.writeValueAsString(aiMessage.toolExecutionRequests()));
                    }
                }
                case ToolExecutionResultMessage toolMessage -> {
                    builder.messageType(MessageTypeEnum.TOOL_EXECUTION_RESULT);
                    builder.toolCallId(toolMessage.id());
                    builder.toolName(toolMessage.toolName());
                    builder.text(toolMessage.text());
                }
                case SystemMessage systemMessage -> {
                    builder.messageType(MessageTypeEnum.SYSTEM);
                    builder.text(systemMessage.text());
                }
                case null, default ->
                        throw new IllegalArgumentException("不支持的 ChatMessage 类型: " + message.getClass().getName());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 ChatMessage 到实体时出错", e);
        }

        return builder.build();
    }

    /**
     * 实例方法：将当前的数据库实体转换为 langchain4j 的 ChatMessage DTO。
     * 在此方法中执行反序列化操作。
     */
    public ChatMessage toChatMessage(ObjectMapper objectMapper) {
        try {
            switch (this.getMessageType()) {
                case USER:
                    List<Content> contentsList = Collections.emptyList();
                    if (this.getContents() != null && !this.getContents().isEmpty()) {
                        contentsList = objectMapper.readValue(this.getContents(), new TypeReference<>() {});
                    }
                    return UserMessage.from(contentsList);
                case AI:
                    List<ToolExecutionRequest> requests = Collections.emptyList();
                    if (this.getToolExecutionRequests() != null && !this.getToolExecutionRequests().isEmpty()) {
                        requests = objectMapper.readValue(this.getToolExecutionRequests(), new TypeReference<>() {});
                    }
                    return AiMessage.from(this.getText(), requests);
                case TOOL_EXECUTION_RESULT:
                    return ToolExecutionResultMessage.from(this.getToolCallId(), this.getToolName(), this.getText());
                case SYSTEM:
                    return SystemMessage.from(this.getText());
                default:
                    throw new IllegalStateException("从数据库读取到未知的消息类型: " + this.getMessageType());
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("从实体反序列化 ChatMessage 时出错", e);
        }
    }
}
