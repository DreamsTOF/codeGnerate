package com.dream.codegenerate.mapper;

import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.BaseMapper;
import com.dream.codegenerate.model.entity.AppVersion;

/**
 * 应用版本 映射层。
 *
 *
 */
@UseDataSource("mysql")
public interface AppVersionMapper extends BaseMapper<AppVersion> {

}
