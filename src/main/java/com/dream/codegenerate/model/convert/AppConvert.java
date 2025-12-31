package com.dream.codegenerate.model.convert;

import com.dream.codegenerate.model.entity.App;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.vo.AppVO;
import com.dream.codegenerate.model.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * App 转换器
 * <p>
 * 实现了 SmartConvert 接口，支持零反射调用。
 * </p>
 */
@Mapper(componentModel = "spring")
public abstract class AppConvert implements SmartConvert<AppVO> {

    public static final AppConvert INSTANCE = Mappers.getMapper(AppConvert.class);

    protected UserConvert userConvert = UserConvert.INSTANCE;

    /**
     * 【核心优化】实现通用接口，替代反射调用
     * 手动分发参数，虽然写起来繁琐一点，但运行性能是最高的
     */
    @Override
    public AppVO toVO(Object... args) {
        if (args == null || args.length == 0) return null;

        // 根据参数数量分发给不同的 MapStruct 方法
        if (args.length == 1) {
            return toVO((App) args[0]);
        }
        if (args.length == 2) {
            return toVO((App) args[0], (User) args[1]);
        }
        return null;
    }

    // ------------------- MapStruct Methods -------------------

    /**
     * SmartQuery 会扫描到这个方法签名，知道需要 (App, User) 两个参数
     */
    public AppVO toVO(App app, User user) {
        if (app == null) return null;

        AppVO appVO = this.toVO(app);

        if (user != null) {
            UserVO userVO = userConvert.toVO(user);
            appVO.setUser(userVO);
        }

        return appVO;
    }

    @Mapping(target = "user", ignore = true)
    public abstract AppVO toVO(App app);

    @Mapping(target = "editTime", ignore = true)
    @Mapping(target = "isDelete", ignore = true)
    @Mapping(target = "currentVersion", ignore = true)
    public abstract App toEntity(AppVO appVO);

    public abstract List<AppVO> toVOList(List<App> appList);
}
