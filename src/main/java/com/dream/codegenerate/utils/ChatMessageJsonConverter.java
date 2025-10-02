package com.dream.codegenerate.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageJsonCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 负责将 LangChain4j 的 ChatMessage 对象与 JSON 字符串进行双向转换。
 */
@Component
public class ChatMessageJsonConverter implements ChatMessageJsonCodec {

    private final ObjectMapper objectMapper;

    // 用于正确反序列化泛型 List<ChatMessage>
    private static final TypeReference<List<ChatMessage>> CHAT_MESSAGE_LIST_TYPE = new TypeReference<>() {};

    /**
     * 构造函数注入。
     * 通过 @Qualifier 注解，确保注入的是专门为 LangChain4j 配置的 ObjectMapper。
     * @param objectMapper Spring 容器中名为 "langchain4jObjectMapper" 的 Bean。
     */
    public ChatMessageJsonConverter(@Qualifier("langchain4jObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatMessage messageFromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ChatMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("从 JSON 反序列化 LangChain4j ChatMessage 时出错", e);
        }
    }

    /**
     * 新增：反序列化一个 JSON 字符串为 ChatMessage 列表。
     */
    @Override
    public List<ChatMessage> messagesFromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            // 使用 TypeReference 来处理泛型，确保列表中的元素被正确反序列化为具体子类
            return objectMapper.readValue(json, CHAT_MESSAGE_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("从 JSON 反序列化 LangChain4j ChatMessage 列表时出错", e);
        }
    }

    @Override
    public String messageToJson(ChatMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 LangChain4j ChatMessage 时出错", e);
        }
    }

    /**
     * 新增：序列化一个 ChatMessage 列表为 JSON 字符串。
     */
    @Override
    public String messagesToJson(List<ChatMessage> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化 LangChain4j ChatMessage 列表时出错", e);
        }
    }
}
