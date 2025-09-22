package com.dream.codegenerate.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.dream.codegenerate.model.dto.appVersion.AppVersionQueryRequest;
import com.dream.codegenerate.model.entity.AppVersion;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.vo.appVersion.AppVersionQueryVO;

import java.util.List;

/**
 * 应用版本 服务层。
 *
 * dream
 */
public interface AppVersionService extends IService<AppVersion> {

    Page<AppVersionQueryVO> listByPage(AppVersionQueryRequest appVersionQueryVO, User loginUser);

    QueryWrapper getQueryWrapper(AppVersionQueryRequest appVersionQueryRequest);

    List<AppVersionQueryVO> getAppVersionQueryVOList(List<AppVersion> appVersionList);
}
