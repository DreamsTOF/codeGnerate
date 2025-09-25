package com.dream.codegenerate.config;

import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    /**
     * 推理流式模型（用于 Vue 项目生成，带工具调用）
     */
    @Bean
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
        return OpenAiStreamingChatModel.builder()
                .timeout(Duration.ofMinutes(5))
                .httpClientBuilder(new SpringRestClientBuilder())
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
