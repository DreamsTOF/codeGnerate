package com.dream.codegenerate.service;

import com.mybatisflex.core.service.IService;
import com.dream.codegenerate.model.entity.ChatMessagesEntity;
import dev.langchain4j.data.message.ChatMessage;

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
}
