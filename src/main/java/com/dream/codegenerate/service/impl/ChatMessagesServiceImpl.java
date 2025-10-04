package com.dream.codegenerate.service.impl;

import cn.hutool.json.JSONUtil;
import com.dream.codegenerate.ai.model.ToolResponse;
import com.dream.codegenerate.model.entity.table.ChatMessagesTableDef;
import com.dream.codegenerate.utils.EmbeddingUtils;
import com.dream.codegenerate.utils.JsonProcessorUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.dream.codegenerate.model.entity.ChatMessagesEntity;
import com.dream.codegenerate.mapper.ChatMessagesMapper;
import com.dream.codegenerate.service.ChatMessagesService;
import com.pgvector.PGvector;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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

    @Resource
    private EmbeddingUtils embeddingUtils;

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
        //对content进行切割
        lastMessage.setToolExecutionRequests(JsonProcessorUtil.processToolExecutionRequests(lastMessage.getToolExecutionRequests()));

        String text = getTextForEmbedding(lastMessage);
        Embedding embeddingResult = embeddingUtils.embeddingShortText(text);
        if (embeddingResult != null) {
            // 只有在成功获取到嵌入结果时，才创建PGvector并设置
            lastMessage.setEmbedding(new PGvector(embeddingResult.vector()));
        } else {
            // 如果没有有效的嵌入结果，将字段设置为null
            lastMessage.setEmbedding(null);
        }
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

    @Override
    public int loadChatHistoryToMemory(long appId, UserMessage userMessage, ChatMemory chatMemory, int i) {
        return 0;
    }

    //辅助方法，返回对应消息所需向量化的文本
    private String getTextForEmbedding(ChatMessagesEntity message) {
        return switch (message.getMessageType()) {
            case USER -> JsonProcessorUtil.findFirstFieldValue(message.getContents(), "text");
            case TOOL_EXECUTION_RESULT ->
            {
                String jsonStr = message.getText();
                // 使用工具类解析更安全，可以避免空的JSON字符串等问题
                ToolResponse toolResponse = JSONUtil.toBean(
                        jsonStr,
                        ToolResponse.class
                );
                yield toolResponse.getActionDescription();
            }
            case AI -> message.getText();
            default -> null;
        };
    }
}
