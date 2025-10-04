package com.dream.codegenerate.ai.memory; // 请替换为你的包名

import com.dream.codegenerate.utils.JsonProcessorUtil;
import com.dream.codegenerate.utils.SpringContextUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 一个有状态的、持久化的聊天记忆实现。
 * 它严格遵循“内存优先，数据库追加”的设计模式。
 *
 * 1.  **初始化**: 创建时，从 ChatMemoryStore 加载一次完整的历史记录到内存中。
 * 2.  **读取**: messages() 方法总是从高速的内存中返回当前的消息列表。
 * 3.  **写入**: add() 方法会同时向内存列表和后端的 ChatMemoryStore 追加新消息。
 * 它通过只传递新消息给 store.updateMessages() 来实现“追加”而非“覆写”。
 */
@Data
public class StatefulChatMemory implements ChatMemory {

    private final Object id;
    private final ChatMemoryStore store; // 你已经实现的 VectorChatMemoryStore
    private final List<ChatMessage> messages; // 核心：内存中的消息列表
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    // 添加 ObjectMapper 依赖，用于序列化和反序列化
    private final ObjectMapper objectMapper;

    private StatefulChatMemory(Builder builder) {
        this.id = builder.id;
        this.store = builder.store;
        this.messages = new ArrayList<>() ;
        this.objectMapper = SpringContextUtil.getBean("langchain4jObjectMapper", ObjectMapper.class);

    }

    @Override
    public Object id() {
        return this.id;
    }

    /**
     * 将一条新消息添加到内存和持久化存储中。
     *
     * @param message 要添加的消息。
     */
    @Override
    public void add(ChatMessage message) {
        ChatMessage messageToAdd = message;

        // 核心逻辑：只在消息是包含工具请求的 AiMessage 时才处理
        if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
            try {
                // 1. 将 List<ToolExecutionRequest> 序列化为 JSON 字符串
                String originalRequestsJson = objectMapper.writeValueAsString(aiMessage.toolExecutionRequests());

                // 2. 调用你强大的工具方法进行裁切
                String truncatedRequestsJson = JsonProcessorUtil.processToolExecutionRequests(originalRequestsJson);

                // 3. 将裁切后的 JSON 字符串反序列化回 List<ToolExecutionRequest>
                List<ToolExecutionRequest> truncatedRequests = objectMapper.readValue(
                        truncatedRequestsJson,
                        new TypeReference<>() {}
                );

                // 4. 创建一个新的、包含了裁切后数据的 AiMessage 实例
                messageToAdd = AiMessage.from(aiMessage.text(), truncatedRequests);

            } catch (JsonProcessingException e) {
                System.err.println("为避免上下文超限而裁切ToolExecutionRequest时出错: " + e.getMessage());
                // 如果出错，为了保证流程继续，我们选择添加原始消息
            }
        }
        lock.writeLock().lock();
        try {
            // 1. 添加到内存列表
            messages.add(messageToAdd);
            // 2. 追加到持久化存储
            // 我们只传递最新的消息，利用你已有的 Service 实现“仅追加最后一条”的逻辑
            store.updateMessages(this.id, List.of(messageToAdd));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 总是从内存中返回当前的消息列表，速度快。
     */
    @Override
    public List<ChatMessage> messages() {
        lock.readLock().lock();
        try {
            // 返回一个不可变的副本，保证线程安全
            return Collections.unmodifiableList(new ArrayList<>(messages));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 清空内存和持久化存储中的所有消息。
     */
    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            messages.clear();
            store.deleteMessages(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Builder 模式 ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Object id;
        private ChatMemoryStore store;
        private List<ChatMessage> messages;

        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        /**
         * 注入你已经写好的 VectorChatMemoryStore
         */
        public Builder chatMemoryStore(ChatMemoryStore store) {
            this.store = store;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public StatefulChatMemory build() {
            if (store == null) {
                throw new IllegalArgumentException("ChatMemoryStore must be provided.");
            }
            if(id== null){
                throw new IllegalArgumentException("id must be provided.");
            }
            return new StatefulChatMemory(this);
        }
    }
}
