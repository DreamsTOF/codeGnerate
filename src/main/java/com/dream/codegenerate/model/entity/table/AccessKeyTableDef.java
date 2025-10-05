package com.dream.codegenerate.model.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.table.TableDef;

import java.io.Serial;

/**
 * 访问key 表定义层。
 *
 * @author dream
 */
public class AccessKeyTableDef extends TableDef {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 访问key
     */
    public static final AccessKeyTableDef ACCESS_KEY = new AccessKeyTableDef();

    /**
     * id
     */
    public final QueryColumn ID = new QueryColumn(this, "id");

    /**
     * 兑换码
     */
    public final QueryColumn CD_KEY = new QueryColumn(this, "cdKey");

    /**
     * 是否使用过兑换码，当前是第几位的兑换码
     */
    public final QueryColumn IS_USE = new QueryColumn(this, "isUse");

    /**
     * 访问api的key
     */
    public final QueryColumn API_KEY = new QueryColumn(this, "apiKey");

    /**
     * 关联的用户id
     */
    public final QueryColumn USER_ID = new QueryColumn(this, "userId");

    /**
     * key的ID
     */
    public final QueryColumn API_KEY_ID = new QueryColumn(this, "apiKeyId");

    /**
     * 所有字段。
     */
    public final QueryColumn ALL_COLUMNS = new QueryColumn(this, "*");

    /**
     * 默认字段，不包含逻辑删除或者 large 等字段。
     */
    public final QueryColumn[] DEFAULT_COLUMNS = new QueryColumn[]{ID, USER_ID, API_KEY, IS_USE, CD_KEY, API_KEY_ID};

    public AccessKeyTableDef() {
        super("", "access_key");
    }

    private AccessKeyTableDef(String schema, String name, String alisa) {
        super(schema, name, alisa);
    }

    public AccessKeyTableDef as(String alias) {
        String key = getNameWithSchema() + "." + alias;
        return getCache(key, k -> new AccessKeyTableDef("", "access_key", alias));
    }

}
