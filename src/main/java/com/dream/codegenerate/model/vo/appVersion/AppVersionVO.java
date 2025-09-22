package com.dream.codegenerate.model.vo.appVersion;

import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 应用版本详情VO
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
     * 关联的对话id，用于追溯版本来源
     */
    private Long chatHistoryId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}