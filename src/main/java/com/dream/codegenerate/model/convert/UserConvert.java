package com.dream.codegenerate.model.convert;

import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.vo.UserVO;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * User 转换器
 */
@Mapper(componentModel = "spring")
public abstract class UserConvert implements SmartConvert<UserVO> {

    public static final UserConvert INSTANCE = Mappers.getMapper(UserConvert.class);

    /**
     * 【核心优化】利用 default 方法实现接口
     */
    @Override
    public UserVO toVO(Object... args) {
        if (args != null && args.length == 1) {
            return toVO((User) args[0]);
        }
        return null;
    }

    public abstract UserVO toVO(User user);

    @Mapping(target = "userPassword", ignore = true)
    @Mapping(target = "vipCode", ignore = true)
    @Mapping(target = "vipNumber", ignore = true)
    @Mapping(target = "vipExpireTime", ignore = true)
    @Mapping(target = "isDelete", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "editTime", ignore = true)
    public abstract User toEntity(UserVO userVO);

    public abstract List<UserVO> toVOList(List<User> userList);
}
