package com.dream.codegenerate.log.autoAudit;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.dialect.DbType;
import com.mybatisflex.core.table.TableInfo;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 审计方言支持 - 解决不同数据库的 JSON 快照与 SQL 差异
 */
public class AuditDialectSupport {

    /**
     * 获取当前数据库类型
     */
    public static DbType getDbType() {
        return FlexGlobalConfig.getDefaultConfig().getDbType();
    }

    /**
     * 构建 JSON 对象函数的 SQL 片段
     * @param info 表元数据
     * @return 类似 JSON_OBJECT('col1', col1, 'col2', col2) 的字符串
     */
    public static String buildJsonSnapshotSql(TableInfo info) {
        DbType dbType = getDbType();
        String[] columns = info.getAllColumns();

        return switch (dbType) {
            case MYSQL, H2 -> Arrays.stream(columns)
                    .map(col -> String.format("'%s', %s", col, col))
                    .collect(Collectors.joining(", ", "JSON_OBJECT(", ")"));

            case POSTGRE_SQL -> Arrays.stream(columns)
                    .map(col -> String.format("'%s', %s", col, col))
                    .collect(Collectors.joining(", ", "json_build_object(", ")"));

            case ORACLE -> Arrays.stream(columns)
                    .map(col -> String.format("KEY '%s' VALUE %s", col, col))
                    .collect(Collectors.joining(", ", "JSON_OBJECT(", ")"));

            default -> {
                // 兜底方案：如果不持支 JSON 函数，则返回所有列拼接（较重，不建议）
                yield String.join(", ", columns);
            }
        };
    }

    /**
     * 处理 SQL 转义，防止简单的注入并适配方言
     */
    public static String sqlEscape(Object v) {
        if (v instanceof Number) return String.valueOf(v);
        return "'" + String.valueOf(v).replace("'", "''") + "'";
    }
}
