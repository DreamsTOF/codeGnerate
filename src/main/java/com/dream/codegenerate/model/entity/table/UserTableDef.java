package com.dream.codegenerate.model.entity.table;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.table.TableDef;

import java.io.Serial;

/**
 * 用户 表定义层。
 *
 * @author dream
 */
public class UserTableDef extends TableDef {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户
     */
    public static final UserTableDef USER = new UserTableDef();

    /**
     * id
     */
    public final QueryColumn ID = new QueryColumn(this, "id");

    /**
     * 会员兑换码
     */
    public final QueryColumn VIP_CODE = new QueryColumn(this, "vipCode");

    /**
     * 编辑时间
     */
    public final QueryColumn EDIT_TIME = new QueryColumn(this, "editTime");

    /**
     * 是否删除
     */
    public final QueryColumn IS_DELETE = new QueryColumn(this, "isDelete");

    /**
     * 用户昵称
     */
    public final QueryColumn USER_NAME = new QueryColumn(this, "userName");

    /**
     * 用户角色：user/admin
     */
    public final QueryColumn USER_ROLE = new QueryColumn(this, "userRole");

    /**
     * 会员编号
     */
    public final QueryColumn VIP_NUMBER = new QueryColumn(this, "vipNumber");

    /**
     * 创建时间
     */
    public final QueryColumn CREATE_TIME = new QueryColumn(this, "createTime");

    /**
     * 更新时间
     */
    public final QueryColumn UPDATE_TIME = new QueryColumn(this, "updateTime");

    /**
     * 用户头像
     */
    public final QueryColumn USER_AVATAR = new QueryColumn(this, "userAvatar");

    /**
     * 账号
     */
    public final QueryColumn USER_ACCOUNT = new QueryColumn(this, "userAccount");

    /**
     * 用户简介
     */
    public final QueryColumn USER_PROFILE = new QueryColumn(this, "userProfile");

    /**
     * 密码
     */
    public final QueryColumn USER_PASSWORD = new QueryColumn(this, "userPassword");

    /**
     * 会员过期时间
     */
    public final QueryColumn VIP_EXPIRE_TIME = new QueryColumn(this, "vipExpireTime");

    /**
     * 所有字段。
     */
    public final QueryColumn ALL_COLUMNS = new QueryColumn(this, "*");

    /**
     * 默认字段，不包含逻辑删除或者 large 等字段。
     */
    public final QueryColumn[] DEFAULT_COLUMNS = new QueryColumn[]{ID, USER_ACCOUNT, USER_PASSWORD, USER_NAME, USER_AVATAR, USER_PROFILE, USER_ROLE, VIP_CODE, VIP_NUMBER, EDIT_TIME, CREATE_TIME, UPDATE_TIME, VIP_EXPIRE_TIME};

    public UserTableDef() {
        super("", "user");
    }

    private UserTableDef(String schema, String name, String alisa) {
        super(schema, name, alisa);
    }

    public UserTableDef as(String alias) {
        String key = getNameWithSchema() + "." + alias;
        return getCache(key, k -> new UserTableDef("", "user", alias));
    }

}
