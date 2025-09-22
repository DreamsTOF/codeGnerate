package com.dream.codegenerate.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.dream.codegenerate.constant.UserConstant;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.exception.ThrowUtils;
import com.dream.codegenerate.model.dto.appVersion.AppVersionQueryRequest;
import com.dream.codegenerate.model.entity.App;
import com.dream.codegenerate.model.entity.AppVersion;
import com.dream.codegenerate.mapper.AppVersionMapper;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.vo.appVersion.AppVersionQueryVO;
import com.dream.codegenerate.service.AppService;
import com.dream.codegenerate.service.AppVersionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mybatisflex.core.query.QueryMethods.ifNull;

/**
 * 应用版本 服务层实现。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
@Service
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion>  implements AppVersionService{

    @Resource
    private AppService appService;

    @Override
    public Page<AppVersionQueryVO> listByPage(AppVersionQueryRequest appVersionQueryRequest, User loginUser) {
        ThrowUtils.throwIf(appVersionQueryRequest.getAppId() == null || appVersionQueryRequest.getAppId() <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(appVersionQueryRequest.getPageSize() <= 0 || appVersionQueryRequest.getPageSize() > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appVersionQueryRequest.getAppId());
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        QueryWrapper queryWrapper = this.getQueryWrapper(appVersionQueryRequest);
        Page<AppVersion> page = this.page(Page.of(1, appVersionQueryRequest.getPageSize()), queryWrapper);
        // 数据脱敏
        Page<AppVersionQueryVO> AppVersionQueryVOPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        List<AppVersionQueryVO> appVersionQueryVOList = this.getAppVersionQueryVOList(page.getRecords());
        AppVersionQueryVOPage.setRecords(appVersionQueryVOList);
        return AppVersionQueryVOPage;
    }


    /**
     * 获取查询包装类
     *
     * @param appVersionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(AppVersionQueryRequest appVersionQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (appVersionQueryRequest == null) {
            return queryWrapper;
        }
        Long appId = appVersionQueryRequest.getAppId();
        String storageType = appVersionQueryRequest.getStorageType();
        Long chatHistoryId = appVersionQueryRequest.getChatHistoryId();
        LocalDateTime lastCreateTime = appVersionQueryRequest.getLastCreateTime();
        queryWrapper.eq("appId", appId);
        if(ObjUtil.isNotEmpty(chatHistoryId)){
            queryWrapper.eq("chatHistoryId", chatHistoryId);
        }
        if(ObjUtil.isNotEmpty(storageType)){
            queryWrapper.eq("storageType", storageType);
        }
        if(ObjUtil.isNotEmpty(lastCreateTime)){
            queryWrapper.lt("createTime", lastCreateTime);
        }
        return queryWrapper;
    }


    @Override
    public List<AppVersionQueryVO> getAppVersionQueryVOList(List<AppVersion> appVersionList) {
        if (CollUtil.isEmpty(appVersionList)) {
            return new ArrayList<>();
        }
        return appVersionList.stream()
                .map(AppVersion::toAppVersionQueryVO)
                .collect(Collectors.toList());
    }

}
