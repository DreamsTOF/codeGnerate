package com.dream.codegenerate.config;

import dev.langchain4j.http.client.spring.restclient.SpringRestClientBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Classname EmbeddingCongif
 * Description
 * Date 2025/10/4 14:48
 * Created by womon
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.embedding-model")
@Data
public class EmbeddingConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;
    @Bean
    public EmbeddingModel openAiEmbeddingModels() {
        // 推荐使用 text-embedding-3-small，性价比高
        return OpenAiEmbeddingModel.builder()
                .httpClientBuilder(new SpringRestClientBuilder())
                .dimensions(1536)
                .baseUrl(baseUrl)
                .apiKey(apiKey) // 替换为你的 OpenAI API ey
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
