package com.dream.codegenerate.model.dto.appVersion;

/**
 * Classname App
 * Description
 * Date 2025/9/21 22:11
 * Created by womon
 */

import com.dream.codegenerate.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话历史查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class AppVersionQueryRequest extends PageRequest implements Serializable {

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 游标查询 - 最后一条记录的创建时间
     * 用于分页查询，获取早于此时间的记录
     */
    private LocalDateTime lastCreateTime;

    /**
     * 存储类型 (full, diff)
     */
    private String storageType;

    /**
     * 关联的对话id，用于追溯版本来源
     */
    private Long chatHistoryId;
}
