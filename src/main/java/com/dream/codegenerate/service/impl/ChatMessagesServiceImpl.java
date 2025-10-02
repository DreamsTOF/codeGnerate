package com.dream.codegenerate.service.impl;

import com.dream.codegenerate.model.entity.table.ChatMessagesTableDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.dream.codegenerate.model.entity.ChatMessagesEntity;
import com.dream.codegenerate.mapper.ChatMessagesMapper;
import com.dream.codegenerate.service.ChatMessagesService;
import dev.langchain4j.data.message.ChatMessage;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 *  服务层实现。
 *
 * @author dream
 */
@Service
public class ChatMessagesServiceImpl extends ServiceImpl<ChatMessagesMapper, ChatMessagesEntity>  implements ChatMessagesService{

    @Resource
    private ObjectMapper langchain4jObjectMapper;

    @Resource
    private  TransactionTemplate transactionTemplate;

    /**
     * 根据 memoryId 获取消息列表，并调用实体类的静态方法进行转换。
     */
    @Override
    public List<ChatMessage> getMessages(String memoryId) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(ChatMessagesTableDef.CHAT_MESSAGES.MEMORY_ID.eq(memoryId))
                .orderBy(ChatMessagesTableDef.CHAT_MESSAGES.CREATED_AT.asc());

        List<ChatMessagesEntity> messageEntities = list(queryWrapper);

        return messageEntities.stream()
                .map(entity -> entity.toChatMessage(langchain4jObjectMapper)) // 调用实体类的实例方法进行转换
                .collect(Collectors.toList());
    }

    /**
     * 更新一个会话的所有消息，并调用实体类的静态方法进行转换。
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        ChatMessagesEntity lastMessage = ChatMessagesEntity.from(memoryId.toString(), messages.getLast(), langchain4jObjectMapper);
        this.save(lastMessage);
    }

    /**
     * 删除一个会话的所有消息。
     * 使用 TransactionTemplate 保证删除操作的原子性。
     */
    @Override
    public void deleteMessages(Object memoryId) {
        transactionTemplate.execute(status -> {
            QueryWrapper deleteWrapper = QueryWrapper.create()
                    .where(ChatMessagesTableDef.CHAT_MESSAGES.MEMORY_ID.eq(memoryId.toString()));
            remove(deleteWrapper);
            return null;
        });
    }
}
