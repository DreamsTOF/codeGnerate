package com.dream.codegenerate.mapper;

import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.BaseMapper;
import com.dream.codegenerate.model.entity.User;

/**
 * 用户 映射层。
 *
 *
 */
@UseDataSource("mysql")
public interface UserMapper extends BaseMapper<User> {

}
