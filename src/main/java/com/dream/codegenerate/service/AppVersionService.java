package com.dream.codegenerate.service;

import com.dream.codegenerate.model.dto.appVersion.AppVersionCompareRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionQueryRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionRestoreRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionSaveRequest;
import com.dream.codegenerate.model.entity.AppVersion;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.vo.appVersion.AppVersionCompareVO;
import com.dream.codegenerate.model.vo.appVersion.AppVersionQueryVO;
import com.dream.codegenerate.model.vo.appVersion.AppVersionVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 应用版本 服务层。
 *
 * dream
 */
public interface AppVersionService extends IService<AppVersion> {

    @Transactional
    Long createNewVersion(AppVersionSaveRequest appVersionSaveRequest, User loginUser);

    Page<AppVersionQueryVO> listByPage(AppVersionQueryRequest appVersionQueryVO, User loginUser);

    QueryWrapper getQueryWrapper(AppVersionQueryRequest appVersionQueryRequest);

    List<AppVersionQueryVO> getAppVersionQueryVOList(List<AppVersion> appVersionList);

    /**
     * 恢复应用版本
     */
    Boolean restore(AppVersionRestoreRequest appVersionRestoreRequest, User loginUser);

    AppVersionVO getAppVersionVOById(long id, User loginUser);

    /**
     * 对比应用版本
     */
    AppVersionCompareVO compare(AppVersionCompareRequest appVersionCompareRequest, User loginUser);

    boolean deleteByAppId(Long appId, User loginUser);
}
