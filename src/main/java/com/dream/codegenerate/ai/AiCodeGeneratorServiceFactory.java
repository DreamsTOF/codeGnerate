package com.dream.codegenerate.ai;

import com.dream.codegenerate.ai.memory.StatefulChatMemory;
import com.dream.codegenerate.ai.memory.VectorChatMemoryStore;
import com.dream.codegenerate.config.TtdChatModelConfig;
import com.dream.codegenerate.service.ChatMessagesService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.dream.codegenerate.ai.guardrail.PromptSafetyInputGuardrail;
import com.dream.codegenerate.ai.tools.*;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.model.enums.CodeGenTypeEnum;
import com.dream.codegenerate.service.ChatHistoryService;
import com.dream.codegenerate.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * AI 服务创建工厂
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private TtdChatModelConfig ttdChatModelConfig;

    @Resource
    private VectorChatMemoryStore vectorChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ChatMessagesService chatMessagesService;

    @Resource
    private ToolManager toolManager;

    @Value("classpath:prompt/codegen-vue-project-system-prompt.txt")
    private org.springframework.core.io.Resource vueProjectPromptResource;

    @Value("classpath:prompt/codegen-html-system-prompt.txt")
    private org.springframework.core.io.Resource htmlPromptResource;

    @Value("classpath:prompt/codegen-multi-file-system-prompt.txt")
    private org.springframework.core.io.Resource multiFilePromptResource;

    // 3. 用于缓存Prompt文件内容的字段
    private  String vueProjectPromptContent;
    private  String htmlPromptContent;
    private  String multiFilePromptContent;

    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause);
            })
            .build();
    @PostConstruct
    public void initializePrompts() {
        log.info("开始加载 AI System Prompts...");
        try {
            vueProjectPromptContent = readResourceAsString(vueProjectPromptResource);
            htmlPromptContent = readResourceAsString(htmlPromptResource);
            multiFilePromptContent = readResourceAsString(multiFilePromptResource);
            log.info("所有 AI System Prompts 加载成功!");
        } catch (IOException e) {
            log.error("加载 AI System Prompt 文件时发生致命错误", e);
            throw new IllegalStateException("无法加载必要的AI Prompt配置文件", e);
        }
    }

    private String readResourceAsString(org.springframework.core.io.Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    /**
     * 根据 appId 获取服务（为了兼容老逻辑）
     *
     * @param appId
     * @return
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId,new UserMessage("请根据用户输入生成代码，请勿直接输出代码，请勿输出任何提示"), CodeGenTypeEnum.HTML, "apiKey");
    }

    /**
     * 根据 appId 获取服务
     *
     * @param appId       应用 id
     * @param codeGenType 生成类型
     * @return
     * @deprecated 请使用 getUnifiedAiCodeGeneratorService 方法替代。
     * 此方法保留是为了向后兼容，它使用的是旧的、非统一的 AI 服务创建逻辑。
     */
//    @Deprecated
//    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
//        String cacheKey = buildCacheKey(appId, codeGenType);
//        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
//    }

    /**
     * 创建新的 AI 服务实例
     *
     * @param appId       应用 id
     * @param codeGenType 生成类型
     * @return
     * @deprecated 旧的服务创建逻辑，仅为兼容保留。
     * 新逻辑请参见 createUnifiedAiService 方法。
     */
    @Deprecated
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
        log.info("为 appId: {} 创建新的 AI 服务实例", appId);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(vectorChatMemoryStore)
                .maxMessages(10)
                .build();
        // 从数据库中加载对话历史到记忆中
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        return switch (codeGenType) {
            // Vue 项目生成，使用工具调用和推理模型
            case VUE_PROJECT -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(reasoningStreamingChatModel)
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools(toolManager.getAllTools())
                        // 处理工具调用幻觉问题
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,
                                        "Error: there is no tool called " + toolExecutionRequest.name())
                        )
                        .maxSequentialToolsInvocations(50)  // 最多连续调用 50 次工具
                        .inputGuardrails(new PromptSafetyInputGuardrail()) // 添加输入护轨
//                        .outputGuardrails(new RetryOutputGuardrail()) // 添加输出护轨，为了流式输出，这里不使用
                        .build();
            }
            // HTML 和 多文件生成，使用流式对话模型
            case HTML, MULTI_FILE -> {
                // 使用多例模式的 StreamingChatModel 解决并发问题
                StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemory(chatMemory)
                        .tools(toolManager.getAllTools())
                        // 处理工具调用幻觉问题
                        .hallucinatedToolNameStrategy(toolExecutionRequest ->
                                ToolExecutionResultMessage.from(toolExecutionRequest,
                                        "Error: there is no tool called " + toolExecutionRequest.name())
                        )
                        .inputGuardrails(new PromptSafetyInputGuardrail()) // 添加输入护轨
//                        .outputGuardrails(new RetryOutputGuardrail()) // 添加输出护轨，为了流式输出，这里不使用
                        .build();
            }
            default ->
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType.getValue());
        };
    }
     /**
      * 【推荐】根据 appId 和生成类型获取 AI 服务
      * 此方法会根据任务复杂度选择合适的模型，以优化成本。
      *
      * @param appId       应用 id
      * @param codeGenType 生成类型
      * @param apiKey
      * @return 统一的 AiCodeGeneratorService 实例
      */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, UserMessage userMessage, CodeGenTypeEnum codeGenType, String apiKey) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey, key -> createAiService(appId,userMessage, codeGenType,apiKey));
    }

    /**
     * 创建 AI 服务实例的核心方法
     *
     * @param appId       应用 id
     * @param codeGenType 生成类型
     * @param apiKey
     * @return AiCodeGeneratorService 实例
     */
    private AiCodeGeneratorService createAiService(long appId, UserMessage userMessage, CodeGenTypeEnum codeGenType, String apiKey) {
        if (appId==0){
            appId=165456418;
        }
        log.info("为 appId: {} 和类型: {} 创建 AI 服务实例", appId, codeGenType.getValue());

        // 1. 通用的内存管理
//        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
//                .builder()
//                .id(appId)
//                .chatMemoryStore(vectorChatMemoryStore)
////                .chatMemoryStore(redisChatMemoryStore)
//                .maxMessages(1000)
//                .build();
        StatefulChatMemory chatMemory = StatefulChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
//                .messages(chatMessagesService.loadChatHistory(appId, userMessage, 1000))
                .build();
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 1000);


        // 2. 根据任务类型选择不同的模型和 Prompt
        StreamingChatModel selectedModel;
        final String systemPromptContent;
        switch (codeGenType) {
            case VUE_PROJECT:
                log.info("任务类型: {}, 选择【高级推理模型】", codeGenType.getValue());
                selectedModel = SpringContextUtil.getBean("anthropicStreamingChatModelPrototype", StreamingChatModel.class);
                systemPromptContent = this.vueProjectPromptContent;;
                break;

            case HTML:
            case MULTI_FILE:
                log.info("任务类型: {}, 选择【标准基础模型】", codeGenType.getValue());
                selectedModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
                systemPromptContent = (codeGenType == CodeGenTypeEnum.HTML)
                        ? this.htmlPromptContent // <-- 使用缓存的字段
                        : this.multiFilePromptContent; // <-- 使用缓存的字段
                break;

            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的代码生成类型");
        }

        // 4. 构建统一的 AI 服务
        return AiServices.builder(AiCodeGeneratorService.class)
//                .chatModel(chatModel)
                .streamingChatModel(getStreamingChatModel(apiKey)) // <--- 使用动态选择的模型
                .chatMemoryProvider(memoryId -> chatMemory)
                .tools(toolManager.getAllTools())
                .systemMessageProvider(chat -> systemPromptContent) // <--- 提供加载好的 Prompt 内容
                .hallucinatedToolNameStrategy(toolExecutionRequest ->
                        ToolExecutionResultMessage.from(toolExecutionRequest,
                                "Error: there is no tool called " + toolExecutionRequest.name())
                )
                .maxSequentialToolsInvocations(1000)
                .build();
    }

    /**
     * 创建 AI 代码生成器服务
     *
     * @return
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0, null, CodeGenTypeEnum.HTML, "apiKey");
    }

    /**
     * 构造缓存键
     *
     * @param appId
     * @param codeGenType
     * @return
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }

    /**
     * 推理流式模型（用于 Vue 项目生成，带工具调用）
     */

    private StreamingChatModel getStreamingChatModel(String apiKey) {
        return AnthropicStreamingChatModel.builder()
                .timeout(Duration.ofMinutes(50))
                .httpClientBuilder(new SpringRestClientBuilder())
                .cacheSystemMessages(true)
                .apiKey(apiKey)
                .baseUrl(ttdChatModelConfig.getBaseUrl())
                .modelName(ttdChatModelConfig.getModelName())
                .maxTokens(ttdChatModelConfig.getMaxTokens())
                .temperature(ttdChatModelConfig.getTemperature())
                .logRequests(ttdChatModelConfig.getLogRequests())
                .logResponses(ttdChatModelConfig.getLogResponses())
                .build();
    }
}
