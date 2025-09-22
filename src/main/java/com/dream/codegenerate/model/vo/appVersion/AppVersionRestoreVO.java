package com.dream.codegenerate.model.vo.appVersion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 应用版本恢复响应
 */
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class AppVersionRestoreVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 代码内容
     */
    private String content;

    /**
     * 版本说明
     */
    private String message;

    /**
     * 创建时间
     */
    private String createTime;
}