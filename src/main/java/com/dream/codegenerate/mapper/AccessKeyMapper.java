package com.dream.codegenerate.mapper;

import com.dream.codegenerate.model.entity.AccessKey;
import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.BaseMapper;

/**
 * 访问key 映射层。
 *
 * @author dream
 */
@UseDataSource("mysql")
public interface AccessKeyMapper extends BaseMapper<AccessKey> {

}
