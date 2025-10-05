package com.dream.codegenerate.service;

import com.dream.codegenerate.model.entity.AccessKey;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 访问key 服务层。
 *
 * @author dream
 */
public interface AccessKeyService extends IService<AccessKey> {

    String getApiKey(Long userId);

    String getCdKey(HttpServletRequest request);

    AccessKey getInfo(HttpServletRequest request);

    boolean useCdKey(HttpServletRequest request);
}
