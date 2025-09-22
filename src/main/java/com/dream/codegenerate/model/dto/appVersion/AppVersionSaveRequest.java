package com.dream.codegenerate.model.dto.appVersion;

import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 应用版本保存请求
 */
@Data
public class AppVersionSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 代码内容（JSON格式）
     */
    private String content;

    /**
     * 存储类型 (full, diff)
     */
    private AppVersionStoreTypeEnum storageType;

    /**
     * 版本说明，类似于 git commit message
     */
    private String message;

    /**
     * 关联的对话id，用于追溯版本来源
     */
    private Long chatHistoryId;
}