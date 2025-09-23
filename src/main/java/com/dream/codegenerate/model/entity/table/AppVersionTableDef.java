package com.dream.codegenerate.model.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.table.TableDef;

/**
 * 应用版本 表定义层。
 *
 * @author MyBatis-Flex Codegen
 */
public class AppVersionTableDef extends TableDef {

    /**
     * 应用版本
     */
    public static final AppVersionTableDef APP_VERSION = new AppVersionTableDef();

    /**
     * id
     */
    public final QueryColumn ID = new QueryColumn(this, "id");

    /**
     * 应用id
     */
    public final QueryColumn APP_ID = new QueryColumn(this, "appId");

    /**
     * 版本号，例如 1, 2, 3...
     */
    public final QueryColumn VERSION = new QueryColumn(this, "version");

    /**
     * 封面图片url
     */
    public final QueryColumn COVER = new QueryColumn(this, "cover");

    /**
     * 存储内容（全量代码或差异 patch）
     */
    public final QueryColumn CONTENT = new QueryColumn(this, "content");

    /**
     * 存储类型 (full, diff)
     */
    public final QueryColumn STORAGE_TYPE = new QueryColumn(this, "storageType");

    /**
     * 版本说明，类似于 git commit message
     */
    public final QueryColumn MESSAGE = new QueryColumn(this, "message");

    /**
     * 关联的对话id，用于追溯版本来源
     */
    public final QueryColumn CHAT_HISTORY_ID = new QueryColumn(this, "chatHistoryId");

    /**
     * 创建时间
     */
    public final QueryColumn CREATE_TIME = new QueryColumn(this, "createTime");

    /**
     * 更新时间
     */
    public final QueryColumn UPDATE_TIME = new QueryColumn(this, "updateTime");

    /**
     * 是否删除
     */
    public final QueryColumn IS_DELETE = new QueryColumn(this, "isDelete");

    /**
     * 所有列
     */
    public final QueryColumn ALL_COLUMNS = new QueryColumn(this, "*");

    /**
     * 默认 schema
     */
    public static final String SCHEMA = null;

    /**
     * 默认表名
     */
    public static final String TABLE_NAME = "app_version";

    public AppVersionTableDef() {
        super(SCHEMA, TABLE_NAME);
    }
}
