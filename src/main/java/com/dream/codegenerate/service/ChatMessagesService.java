package com.dream.codegenerate.service;

import com.mybatisflex.core.service.IService;
import com.dream.codegenerate.model.entity.ChatMessagesEntity;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.List;

/**
 *  服务层。
 *
 * @author dream
 */
public interface ChatMessagesService extends IService<ChatMessagesEntity> {

    List<ChatMessage> getMessages(String memoryId);

    void updateMessages(Object memoryId, List<ChatMessage> messages);

    void deleteMessages(Object memoryId);

    int loadChatHistoryToMemory(long appId, UserMessage userMessage, ChatMemory chatMemory, int i);
}
