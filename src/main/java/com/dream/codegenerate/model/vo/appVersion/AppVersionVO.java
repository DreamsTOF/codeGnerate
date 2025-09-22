package com.dream.codegenerate.model.vo.appVersion;

import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Classname AppVersionVO
 * Description
 * Date 2025/9/21 22:19
 * Created by womon
 */
@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class AppVersionVO {

    /**
     * id
     */
    private Long id;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 版本号，例如 1, 2, 3...
     */
    private Integer version;

    /**
     * 存储内容（全量代码或差异 patch）
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
     * 创建时间
     */

    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
