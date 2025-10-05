package com.dream.codegenerate.ai;

import com.dream.codegenerate.config.RoutingAiModelConfig;
import com.dream.codegenerate.utils.SpringContextUtil;
import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * AI代码生成类型路由服务工厂
 *
 * @author yupi
 */
@Slf4j
@Configuration
public class AiCodeGenTypeRoutingServiceFactory {

    @Resource
    private RoutingAiModelConfig routingAiModelConfig;

    /**
     * 创建AI代码生成类型路由服务实例
     */
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService(String apiKey) {
//        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(getChatModel(apiKey))
                .build();
    }

    public ChatModel getChatModel(String apiKey) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .httpClientBuilder(new SpringRestClientBuilder())
                .modelName(routingAiModelConfig.getModelName())
                .baseUrl(routingAiModelConfig.getBaseUrl())
                .maxTokens(routingAiModelConfig.getMaxTokens())
                .temperature(routingAiModelConfig.getTemperature())
                .logRequests(routingAiModelConfig.getLogRequests())
                .logResponses(routingAiModelConfig.getLogResponses())
                .build();
    }
}
