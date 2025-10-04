package com.dream.codegenerate.service.impl;

import com.dream.codegenerate.model.entity.table.ChatMessagesTableDef;
import com.dream.codegenerate.model.enums.MessageTypeEnum;
import com.dream.codegenerate.utils.EmbeddingUtils;
import com.dream.codegenerate.utils.JsonProcessorUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.dream.codegenerate.model.entity.ChatMessagesEntity;
import com.dream.codegenerate.mapper.ChatMessagesMapper;
import com.dream.codegenerate.service.ChatMessagesService;
import com.pgvector.PGvector;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 *  服务层实现。
 *
 * @author dream
 */
@Service
@Slf4j
public class ChatMessagesServiceImpl extends ServiceImpl<ChatMessagesMapper, ChatMessagesEntity>  implements ChatMessagesService{

    @Resource
    private ObjectMapper langchain4jObjectMapper;

    @Resource
    private  TransactionTemplate transactionTemplate;

    @Resource
    private EmbeddingUtils embeddingUtils;



    /**
     * [已重构] 智能加载和检索消息。
     * 此方法现在是构建上下文的核心，它会在对话开始时（或内存需要填充时）被调用。
     * 它会根据最新的用户消息动态加载最相关的历史记录。
     */
    @Override
    public List<ChatMessage> getMessages(String memoryId) {
        log.info("智能加载 memoryId: {} 的历史记录...", memoryId);

        // 1. 先获取最后一条用户消息，作为检索的依据
        QueryWrapper lastUserMessageQuery = QueryWrapper.create()
                .where(ChatMessagesTableDef.CHAT_MESSAGES.MEMORY_ID.eq(memoryId))
                .and(ChatMessagesTableDef.CHAT_MESSAGES.MESSAGE_TYPE.eq(MessageTypeEnum.USER))
                .orderBy(ChatMessagesTableDef.CHAT_MESSAGES.CREATED_AT.desc())
                .limit(1);

        Optional<ChatMessagesEntity> lastUserMessageEntity = Optional.ofNullable(this.getOne(lastUserMessageQuery));

        // 如果连一条用户消息都没有（例如，全新的对话），则返回空列表
        if (lastUserMessageEntity.isEmpty()) {
            log.warn("memoryId: {} 没有任何用户消息，返回空历史。", memoryId);
            return new ArrayList<>();
        }

        // 我们将使用这条最新的用户消息来执行智能加载
        UserMessage userMessage = (UserMessage) lastUserMessageEntity.get().toChatMessage(langchain4jObjectMapper);

        // 调用我们之前设计的核心加载逻辑
        return loadChatHistory(Long.parseLong(memoryId), userMessage, 10); // 默认检索10条
    }

    /**
     * [已重构] 此方法现在是 getMessages 的核心实现，负责混合检索逻辑。
     * 它不再需要 ChatMemory 参数，而是直接返回一个消息列表。
     */
    @Override
    public List<ChatMessage> loadChatHistory(long appId, UserMessage userMessage, int maxVectorResults) {
        try {
            String memoryId = String.valueOf(appId);
            Map<Long, ChatMessagesEntity> messagesToLoadMap = new LinkedHashMap<>();

            // --- 步骤 1: 加载固定的前4条历史消息 ---
            QueryWrapper oldestQuery = QueryWrapper.create()
                    .where(ChatMessagesTableDef.CHAT_MESSAGES.MEMORY_ID.eq(memoryId))
                    .orderBy(ChatMessagesTableDef.CHAT_MESSAGES.CREATED_AT.asc())
                    .limit(4);
            List<ChatMessagesEntity> mandatoryMessages = this.list(oldestQuery);
            mandatoryMessages.forEach(entity -> messagesToLoadMap.put(entity.getId(), entity));

            // --- 步骤 2: 根据用户消息，从向量库检索相关历史 ---
            Embedding queryEmbedding = embeddingUtils.embeddingShortText(userMessage.singleText());
            if (queryEmbedding != null) {
                PGvector queryVector = new PGvector(queryEmbedding.vector());
                // 实现向量相似性搜索
                List<ChatMessagesEntity> vectorSearchResults = this.mapper.findSimilar(memoryId, queryVector, maxVectorResults);

                vectorSearchResults.forEach(entity -> messagesToLoadMap.putIfAbsent(entity.getId(), entity));
            }

            // --- 步骤 3: 保证工具调用的完整性 ---
            List<ChatMessagesEntity> toolResultsToLoad = new ArrayList<>();
            // 创建一个副本进行遍历，避免在遍历时修改Map
            for (ChatMessagesEntity entity : new ArrayList<>(messagesToLoadMap.values())) {
                // 只关心AI消息
                if (entity.getMessageType() == MessageTypeEnum.AI) {
                    ChatMessage chatMessage = entity.toChatMessage(langchain4jObjectMapper);
                    if (chatMessage instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                        for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {

                            // 调用 Mapper 根据 toolCallId 查询对应的工具执行结果
                            ChatMessagesEntity toolResultEntity = this.mapper.findByToolCallId(memoryId, request.id());

                            if (toolResultEntity != null && !messagesToLoadMap.containsKey(toolResultEntity.getId())) {
                                // 如果找到了结果，并且这个结果不在我们已经选出的消息列表里，就把它加进来
                                toolResultsToLoad.add(toolResultEntity);
                                log.info("补全工具调用链：找到并添加 toolCallId: {} 的结果", request.id());
                            }
                        }
                    }
                }
            }
            toolResultsToLoad.forEach(entity -> messagesToLoadMap.put(entity.getId(), entity));

            // --- 步骤 4: 最终整理并返回 ---
            if (messagesToLoadMap.isEmpty()) return new ArrayList<>();

            List<ChatMessage> finalMessages = messagesToLoadMap.values().stream()
                    .sorted(Comparator.comparing(ChatMessagesEntity::getCreatedAt))
                    .map(entity -> entity.toChatMessage(langchain4jObjectMapper))
                    .collect(Collectors.toList());

            log.info("为 memoryId: {} 智能加载了 {} 条混合历史消息", memoryId, finalMessages.size());
            return finalMessages;

        } catch (Exception e) {
            log.error("加载混合历史对话失败，memoryId: {}, error: {}", appId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }



    /**
     * 更新一个会话的所有消息，并调用实体类的静态方法进行转换。
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        ChatMessagesEntity lastMessage = ChatMessagesEntity.from(memoryId.toString(), messages.getLast(), langchain4jObjectMapper);
        //获取要向量化的数据
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


    //辅助方法，返回对应消息所需向量化的文本
    private String getTextForEmbedding(ChatMessagesEntity message) {
        return switch (message.getMessageType()) {
            case USER -> JsonProcessorUtil.findFirstFieldValue(message.getContents(), "text");
            case AI -> StringUtils.isNotBlank(message.getText())
                    ? message.getText()
                    : JsonProcessorUtil.findActionDescriptionInArguments(message.getToolExecutionRequests());
            default -> null;
        };
    }
}
