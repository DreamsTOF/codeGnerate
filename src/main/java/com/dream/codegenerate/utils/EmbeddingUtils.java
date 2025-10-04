package com.dream.codegenerate.utils;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Classname EmbeddingUtil
 * Description 向量化工具类
 * 封装了文本的切分和向量化逻辑，使其易于在应用中复用。
 * Date 2025/10/4 14:56
 * Created by womon
 */
@Component
public class EmbeddingUtils {


    @Resource
    @Qualifier("openAiEmbeddingModels")
    private EmbeddingModel openAiEmbeddingModels;

    private final DocumentSplitter documentSplitter =DocumentSplitters.recursive(500, 100);;


    /**
     * 将单个短文本字符串转换为向量。
     * 不进行切分，适用于用户查询、摘要等短文本。
     *
     * @param text 需要向量化的文本
     * @return 向量对象 (Embedding)
     */
    public Embedding embeddingShortText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return openAiEmbeddingModels.embed(text).content();
    }

}
