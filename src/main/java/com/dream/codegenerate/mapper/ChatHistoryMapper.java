package com.dream.codegenerate.mapper;

import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.BaseMapper;
import com.dream.codegenerate.model.entity.ChatHistory;

/**
 * 对话历史 映射层。
 *
 *
 */
@UseDataSource("mysql")
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

}
