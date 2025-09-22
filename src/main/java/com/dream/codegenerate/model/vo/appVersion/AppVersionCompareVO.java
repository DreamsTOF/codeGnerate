package com.dream.codegenerate.model.vo.appVersion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 对比应用版本响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppVersionCompareVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 源版本信息
     */
    private AppVersionVO fromVersionData;

    /**
     * 目标版本信息
     */
    private AppVersionVO toVersionData;
}
