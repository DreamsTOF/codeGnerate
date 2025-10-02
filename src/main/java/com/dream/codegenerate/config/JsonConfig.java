package com.dream.codegenerate.config;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXISTING_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;
import static com.fasterxml.jackson.annotation.PropertyAccessor.FIELD;

/**
 * Spring MVC Json 配置
 */
@Configuration
public class JsonConfig {

    /**
     * 添加 Long 转 json 精度丢失的配置
     */
    @Bean
    @Primary
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder
                .createXmlMapper(false)
                .build();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        return objectMapper;
    }

    @Bean
    @Qualifier("langchain4jObjectMapper")
    public ObjectMapper langchain4jObjectMapper() {
        return JsonMapper.builder()
                .visibility(FIELD, ANY)
                .addMixIn(ChatMessage.class, ChatMessageMixin.class)
                .addMixIn(SystemMessage.class, SystemMessageMixin.class)
                .addMixIn(UserMessage.class, UserMessageMixin.class)
                .addMixIn(AiMessage.class, AiMessageMixin.class)
                .addMixIn(ToolExecutionRequest.class, ToolExecutionRequestMixin.class)
                .addMixIn(ToolExecutionResultMessage.class, ToolExecutionResultMessageMixin.class)
                .addMixIn(Content.class, ContentMixin.class)
                .addMixIn(TextContent.class, TextContentMixin.class)
                .build();
    }

    // --- LangChain4j Mixin Definitions ---

    @JsonInclude(NON_NULL)
    @JsonTypeInfo(use = NAME, include = EXISTING_PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SystemMessage.class, name = "SYSTEM"),
            @JsonSubTypes.Type(value = UserMessage.class, name = "USER"),
            @JsonSubTypes.Type(value = AiMessage.class, name = "AI"),
            @JsonSubTypes.Type(value = ToolExecutionResultMessage.class, name = "TOOL_EXECUTION_RESULT"),
    })
    private static abstract class ChatMessageMixin {
        @JsonProperty
        public abstract ChatMessageType type();
    }

    @JsonInclude(NON_NULL)
    private static abstract class SystemMessageMixin {
        @JsonCreator
        public SystemMessageMixin(@JsonProperty("text") String text) {}
    }

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = UserMessage.Builder.class)
    private static abstract class UserMessageMixin {}

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = AiMessage.Builder.class)
    private static abstract class AiMessageMixin {}

    @JsonInclude(NON_NULL)
    @JsonDeserialize(builder = ToolExecutionRequest.Builder.class)
    private static abstract class ToolExecutionRequestMixin {}

    @JsonInclude(NON_NULL)
    private static class ToolExecutionResultMessageMixin {
        @JsonCreator
        public ToolExecutionResultMessageMixin(@JsonProperty("id") String id,
                                               @JsonProperty("toolName") String toolName,
                                               @JsonProperty("text") String text) {
        }


    }
    @JsonInclude(NON_NULL)
    @JsonTypeInfo(use = NAME, include = EXISTING_PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TextContent.class, name = "TEXT"),
            @JsonSubTypes.Type(value = ImageContent.class, name = "IMAGE"),
            @JsonSubTypes.Type(value = AudioContent.class, name = "AUDIO"),
            @JsonSubTypes.Type(value = VideoContent.class, name = "VIDEO"),
            @JsonSubTypes.Type(value = PdfFileContent.class, name = "PDF"),
    })
    private static abstract class ContentMixin {

        @JsonProperty
        public abstract ContentType type();
    }

    @JsonInclude(NON_NULL)
    private static abstract class TextContentMixin {

        @JsonCreator
        public TextContentMixin(@JsonProperty("text") String text) {
        }
    }
}


