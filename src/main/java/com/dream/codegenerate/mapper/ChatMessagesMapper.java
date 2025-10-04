package com.dream.codegenerate.mapper;

import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.BaseMapper;
import com.dream.codegenerate.model.entity.ChatMessagesEntity;
import com.pgvector.PGvector;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 *  映射层。
 *
 * @author dream
 */
@UseDataSource("pg")
public interface ChatMessagesMapper extends BaseMapper<ChatMessagesEntity> {
    /**
     * 根据向量相似性查找相关的聊天记录。
     *
     * @param memoryId       会话ID，用于限定搜索范围
     * @param queryVector    用于查询的向量
     * @param maxResults     返回的最大结果数量
     * @return 相关的聊天记录实体列表
     */
    // 使用 @Select 注解直接编写 SQL，这是处理特殊函数最方便的方式
    @Select("SELECT * FROM chat_messages " +
            "WHERE memory_id = #{memoryId} " +
            "ORDER BY embedding <=> #{queryVector} " + // 核心：使用余弦距离进行排序
            "LIMIT #{maxResults}")
    List<ChatMessagesEntity> findSimilar(
            @Param("memoryId") String memoryId,
            @Param("queryVector") PGvector queryVector,
            @Param("maxResults") int maxResults
    );

    /**
     * [新方法] 根据 tool_call_id 精确查找工具执行结果消息。
     *
     * @param memoryId   会话ID
     * @param toolCallId 工具调用的唯一ID
     * @return 匹配的工具执行结果实体，如果没有则返回null
     */
    @Select("SELECT * FROM chat_messages " +
            "WHERE memory_id = #{memoryId} AND tool_call_id = #{toolCallId} " +
            "LIMIT 1")
    ChatMessagesEntity findByToolCallId(
            @Param("memoryId") String memoryId,
            @Param("toolCallId") String toolCallId
    );
}
