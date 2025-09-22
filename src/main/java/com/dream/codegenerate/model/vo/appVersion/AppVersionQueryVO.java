package com.dream.codegenerate.model.vo.appVersion;

/**
 * Classname App
 * Description
 * Date 2025/9/21 22:11
 * Created by womon
 */

import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 对话历史查询列表封装类
 */
@Data
@Builder
public class AppVersionQueryVO  implements Serializable {

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 版本号，例如 1, 2, 3...
     */
    private Integer version;

    /**
     * 存储类型 (full, diff)
     */
    private AppVersionStoreTypeEnum storageType;

    /**
     * 版本说明，类似于 git commit message
     */
    private String message;

}
