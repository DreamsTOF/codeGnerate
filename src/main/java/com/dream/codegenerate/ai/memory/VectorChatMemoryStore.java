package com.dream.codegenerate.ai.memory;

import com.dream.codegenerate.service.ChatMessagesService;
import com.dream.codegenerate.utils.ChatMessageJsonConverter;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Classname VectorChatMemoryStore
 * Description
 * Date 2025/10/2 09:44
 * Created by womon
 */
@Component
public class VectorChatMemoryStore implements ChatMemoryStore {


    @Resource
    private ChatMessagesService chatMessagesService;


    //根据key获取消息
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return chatMessagesService.getMessages(memoryId.toString());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        chatMessagesService.updateMessages(memoryId.toString(), messages);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMessagesService.deleteMessages(memoryId.toString());
    }
}
