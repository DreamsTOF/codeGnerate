package com.dream.codegenerate.model.dto.appVersion;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用版本恢复请求
 */
@Data
public class AppVersionRestoreRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 版本号
     */
    private Integer version;
}