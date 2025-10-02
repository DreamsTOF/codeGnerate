package com.dream.codegenerate.mapper;

import com.mybatisflex.annotation.UseDataSource;
import com.mybatisflex.core.BaseMapper;
import com.dream.codegenerate.model.entity.App;

/**
 * 应用 映射层。
 *
 *
 */
@UseDataSource("mysql")
public interface AppMapper extends BaseMapper<App> {

}
