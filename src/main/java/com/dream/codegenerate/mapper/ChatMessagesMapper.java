package com.dream.codegenerate.mapper;

import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.BaseMapper;
import com.dream.codegenerate.model.entity.ChatMessagesEntity;

/**
 *  映射层。
 *
 * @author dream
 */
@UseDataSource("pg")
public interface ChatMessagesMapper extends BaseMapper<ChatMessagesEntity> {

}
