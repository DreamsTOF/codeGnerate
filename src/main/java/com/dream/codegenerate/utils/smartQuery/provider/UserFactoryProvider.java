package com.dream.codegenerate.utils.smartQuery.provider;

import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.utils.smartQuery.EntityFactoryProvider;
import com.dream.codegenerate.utils.smartQuery.SmartQueryAssembler.EntityFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * User 实体工厂提供者
 * <p>
 * 自动注册到 SmartQueryConfig，提供极速对象构建能力。
 * 已过滤掉所有 static 业务常量字段。
 * </p>
 */
@Component
public class UserFactoryProvider implements EntityFactoryProvider<User> {

    @Override
    public Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public EntityFactory<User> getFactory() {
        return (row, prefix) -> {
            User user = new User();
            // 能够极大减少字符串拼接开销的判断
            if (prefix == null || prefix.isEmpty()) {
                // 根对象构建 (无前缀)
                user.setId((Long) row.get("id"));
                user.setUserAccount((String) row.get("userAccount"));
                user.setUserPassword((String) row.get("userPassword"));
                user.setUserName((String) row.get("userName"));
                user.setUserAvatar((String) row.get("userAvatar"));
                user.setUserProfile((String) row.get("userProfile"));
                user.setUserRole((String) row.get("userRole"));
                user.setVipCode((String) row.get("vipCode"));
                user.setVipNumber((Long) row.get("vipNumber"));
                user.setEditTime((LocalDateTime) row.get("editTime"));
                user.setCreateTime((LocalDateTime) row.get("createTime"));
                user.setUpdateTime((LocalDateTime) row.get("updateTime"));
                user.setIsDelete((Integer) row.get("isDelete"));
                user.setVipExpireTime((LocalDateTime) row.get("vipExpireTime"));
            } else {
                // 嵌套对象或 Join 对象，必须拼接前缀
                user.setId((Long) row.get(prefix + "id"));
                user.setUserAccount((String) row.get(prefix + "userAccount"));
                user.setUserPassword((String) row.get(prefix + "userPassword"));
                user.setUserName((String) row.get(prefix + "userName"));
                user.setUserAvatar((String) row.get(prefix + "userAvatar"));
                user.setUserProfile((String) row.get(prefix + "userProfile"));
                user.setUserRole((String) row.get(prefix + "userRole"));
                user.setVipCode((String) row.get(prefix + "vipCode"));
                user.setVipNumber((Long) row.get(prefix + "vipNumber"));
                user.setEditTime((LocalDateTime) row.get(prefix + "editTime"));
                user.setCreateTime((LocalDateTime) row.get(prefix + "createTime"));
                user.setUpdateTime((LocalDateTime) row.get(prefix + "updateTime"));
                user.setIsDelete((Integer) row.get(prefix + "isDelete"));
                user.setVipExpireTime((LocalDateTime) row.get(prefix + "vipExpireTime"));
            }
            return user;
        };
    }
}
