package com.dream.codegenerate.manager;

import com.pgvector.PGvector;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.util.PGobject; // 可能需要引入这个类



@MappedTypes(PGvector.class)
public class VectorTypeHandler extends BaseTypeHandler<PGvector> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PGvector parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, parameter);
    }

    // 修正这个方法
    @Override
    public PGvector getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object obj = rs.getObject(columnName);
        return fromObject(obj);
    }

    // 修正这个方法
    @Override
    public PGvector getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object obj = rs.getObject(columnIndex);
        return fromObject(obj);
    }

    // 修正这个方法
    @Override
    public PGvector getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object obj = cs.getObject(columnIndex);
        return fromObject(obj);
    }

    // 新增一个私有辅助方法来处理转换逻辑
    private PGvector fromObject(Object obj) throws SQLException {
        if (obj == null) {
            return null;
        }

        // 如果已经是 PGvector 类型，直接返回（比如某些特殊驱动或场景）
        if (obj instanceof PGvector) {
            return (PGvector) obj;
        }

        // 核心逻辑：从 PGobject 或 String 中创建 PGvector
        if (obj instanceof PGobject || obj instanceof String) {
            try {
                // PGvector 的构造函数可以直接接收向量的字符串形式
                return new PGvector(obj.toString());
            } catch (Exception e) {
                throw new SQLException("Failed to convert object to PGvector", e);
            }
        }

        throw new SQLException("Unsupported object type for PGvector: " + obj.getClass().getName());
    }
}
