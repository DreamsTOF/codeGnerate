package com.dream.codegenerate.model.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.table.TableDef;

import java.io.Serial;

/**
 * 应用 表定义层。
 *
 * @author dream
 */
public class AppTableDef extends TableDef {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用
     */
    public static final AppTableDef APP = new AppTableDef();

    /**
     * id
     */
    public final QueryColumn ID = new QueryColumn(this, "id");

    /**
     * 应用封面
     */
    public final QueryColumn COVER = new QueryColumn(this, "cover");

    /**
     * 创建用户id
     */
    public final QueryColumn USER_ID = new QueryColumn(this, "userId");

    /**
     * 应用名称
     */
    public final QueryColumn APP_NAME = new QueryColumn(this, "appName");

    /**
     * 编辑时间
     */
    public final QueryColumn EDIT_TIME = new QueryColumn(this, "editTime");

    /**
     * 是否删除
     */
    public final QueryColumn IS_DELETE = new QueryColumn(this, "isDelete");

    /**
     * 优先级
     */
    public final QueryColumn PRIORITY = new QueryColumn(this, "priority");

    /**
     * 部署标识
     */
    public final QueryColumn DEPLOY_KEY = new QueryColumn(this, "deployKey");

    /**
     * 创建时间
     */
    public final QueryColumn CREATE_TIME = new QueryColumn(this, "createTime");

    /**
     * 应用初始化的 prompt
     */
    public final QueryColumn INIT_PROMPT = new QueryColumn(this, "initPrompt");

    /**
     * 更新时间
     */
    public final QueryColumn UPDATE_TIME = new QueryColumn(this, "updateTime");

    /**
     * 代码生成类型（枚举）
     */
    public final QueryColumn CODE_GEN_TYPE = new QueryColumn(this, "codeGenType");

    /**
     * 部署时间
     */
    public final QueryColumn DEPLOYED_TIME = new QueryColumn(this, "deployedTime");

    /**
     * 当前应用版本
     */
    public final QueryColumn CURRENT_VERSION = new QueryColumn(this, "currentVersion");

    /**
     * 所有字段。
     */
    public final QueryColumn ALL_COLUMNS = new QueryColumn(this, "*");

    /**
     * 默认字段，不包含逻辑删除或者 large 等字段。
     */
    public final QueryColumn[] DEFAULT_COLUMNS = new QueryColumn[]{ID, APP_NAME, COVER, INIT_PROMPT, CODE_GEN_TYPE, DEPLOY_KEY, DEPLOYED_TIME, PRIORITY, USER_ID, EDIT_TIME, CREATE_TIME, UPDATE_TIME, CURRENT_VERSION};

    public AppTableDef() {
        super("", "app");
    }

    private AppTableDef(String schema, String name, String alisa) {
        super(schema, name, alisa);
    }

    public AppTableDef as(String alias) {
        String key = getNameWithSchema() + "." + alias;
        return getCache(key, k -> new AppTableDef("", "app", alias));
    }

}
