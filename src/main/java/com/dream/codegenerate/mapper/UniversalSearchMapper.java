package com.dream.codegenerate.mapper; // ⚠️ 请修改为你实际的 Mapper 包路径

import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.constant.SqlOperator;

import java.util.List;
import java.util.Map;

/**
 * 通用查询 Mapper
 * 专门用于执行 SmartUniversalExtractor 生成的动态 SQL
 */
@Mapper
public interface UniversalSearchMapper {


    // MyBatis 默认将结果映射为 Map<String, Object>
    // ${sql} 表示直接拼接 SQL，这是实现一枪流的核心
    @Select("${sql}")
    List<Map<String, Object>> executeDynamicUnionQuery(String sql);

}
