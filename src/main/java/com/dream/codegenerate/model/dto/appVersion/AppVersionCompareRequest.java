package com.dream.codegenerate.model.dto.appVersion;

import lombok.Data;

import java.io.Serializable;

/**
 * 对比应用版本请求
 */
@Data
public class AppVersionCompareRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 源版本号
     */
    private Integer fromVersion;

    /**
     * 目标版本号
     */
    private Integer toVersion;
}
