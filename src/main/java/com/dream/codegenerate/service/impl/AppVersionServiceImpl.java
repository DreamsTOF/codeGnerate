package com.dream.codegenerate.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.model.dto.appVersion.AppVersionCompareRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionRestoreRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionSaveRequest;
import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import com.dream.codegenerate.model.vo.appVersion.AppVersionCompareVO;
import com.dream.codegenerate.model.vo.appVersion.AppVersionRestoreVO;
import com.dream.codegenerate.model.vo.appVersion.AppVersionVO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.dream.codegenerate.model.entity.table.AppVersionTableDef.APP_VERSION;
import static com.mybatisflex.core.query.QueryMethods.ifNull;

/**
 * 应用版本 服务层实现。
 *
 * dream
 */
@Service
@Slf4j
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion>  implements AppVersionService{

    @Resource
    @Lazy
    private AppService appService;

    @Transactional
    @Override
    public Long createNewVersion(AppVersionSaveRequest appVersionSaveRequest, User loginUser) {
        Long appId = appVersionSaveRequest.getAppId();
        String codeContent = appVersionSaveRequest.getContent();
        Long chatHistoryId = appVersionSaveRequest.getChatHistoryId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID错误");

        checkAppPermission(appId, loginUser);
        // 1. 获取当前最新版本号
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select(APP_VERSION.VERSION)
                .from(APP_VERSION)
                .where(APP_VERSION.APP_ID.eq(appId))
                .orderBy(APP_VERSION.VERSION.desc())
                .limit(1);
        AppVersion latestVersion = this.getOne(queryWrapper);

        int nextVersion = 1;
        if (latestVersion != null && latestVersion.getVersion() != null) {
            nextVersion = latestVersion.getVersion() + 1;
        }

        // 2. 构建新版本对象并保存
        AppVersion newAppVersion = AppVersion.builder()
                .appId(appId)
                .version(nextVersion)
                .content(codeContent)
                .storageType(AppVersionStoreTypeEnum.FULL) // 默认使用全量存储
                .chatHistoryId(chatHistoryId) // 关联历史对话ID
                .build();

        boolean result = this.save(newAppVersion);
        if (!result || newAppVersion.getId() == null) {
            log.error("自动创建应用版本失败, appId: {}", appId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建版本失败");
        }
        log.info("成功为应用 {} 创建新版本 V{}", appId, nextVersion);
        return newAppVersion.getId();
    }

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

    @Override
    public AppVersionRestoreVO restore(AppVersionRestoreRequest appVersionRestoreRequest, User loginUser) {
        // TODO: 实现恢复版本逻辑
        return null;
    }
    @Override
    public AppVersionVO getAppVersionVOById(long id, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 1. 获取版本数据
        AppVersion appVersion = this.getById(id);
        ThrowUtils.throwIf(appVersion == null, ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        // 2. 权限校验
        checkAppPermission(appVersion.getAppId(), loginUser);
        // 3. 封装并返回
        return AppVersion.toAppVersionVO(appVersion);
    }
    @Override
    public AppVersionCompareVO compare(AppVersionCompareRequest appVersionCompareRequest, User loginUser) {
        Long appId = appVersionCompareRequest.getAppId();
        Integer fromVersionNum = appVersionCompareRequest.getFromVersion();
        Integer toVersionNum = appVersionCompareRequest.getToVersion();

        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID错误");
        ThrowUtils.throwIf(fromVersionNum == null || fromVersionNum <= 0, ErrorCode.PARAMS_ERROR, "起始版本号错误");
        ThrowUtils.throwIf(toVersionNum == null || toVersionNum <= 0, ErrorCode.PARAMS_ERROR, "目标版本号错误");
        ThrowUtils.throwIf(fromVersionNum.equals(toVersionNum), ErrorCode.PARAMS_ERROR, "不能对比相同版本");

        // 2. 权限校验
        checkAppPermission(appId, loginUser);

        // 3. 查询两个版本的完整信息
        AppVersion fromVersion = getOne(
                QueryWrapper.create().where(APP_VERSION.APP_ID.eq(appId)).and(APP_VERSION.VERSION.eq(fromVersionNum))
        );
        ThrowUtils.throwIf(fromVersion == null, ErrorCode.NOT_FOUND_ERROR, "起始版本不存在");

        AppVersion toVersion = getOne(
                QueryWrapper.create().where(APP_VERSION.APP_ID.eq(appId)).and(APP_VERSION.VERSION.eq(toVersionNum))
        );
        ThrowUtils.throwIf(toVersion == null, ErrorCode.NOT_FOUND_ERROR, "目标版本不存在");

        // 4. 封装返回
        AppVersionVO fromVersionVO = AppVersion.toAppVersionVO(fromVersion);
        AppVersionVO toVersionVO = AppVersion.toAppVersionVO(toVersion);

        return AppVersionCompareVO.builder()
                .fromVersionData(fromVersionVO)
                .toVersionData(toVersionVO)
                .build();
    }

    @Override
    public boolean deleteByAppId(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(String.valueOf(APP_VERSION.ID), id);
        return this.remove(queryWrapper);
    }
    /**
     * 内部方法：校验用户对应用的权限
     */
    private void checkAppPermission(Long appId, User loginUser) {
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权操作该应用");
    }
}
