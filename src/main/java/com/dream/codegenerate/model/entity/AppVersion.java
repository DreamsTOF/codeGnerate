package com.dream.codegenerate.model.entity;

import com.dream.codegenerate.log.autoAudit.AuditReference;
import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Column;
import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.*;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 应用版本 实体类
 *
 * @author dream
 * @since
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Table(dataSource ="master",value = "app_version")
@Schema(description = "应用版本")
public class AppVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    @Schema(description = "id")
    private Long id;

    /**
     * 应用id
     */
    @Column(value = "appId")
    @Schema(description = "应用id")
    @AuditReference(target = App.class, label = App.SHOW_APPNAME)
    private Long appId;

    /**
     * 版本号，例如 1, 2, 3...
     */
    @Column(value = "version")
    @Schema(description = "版本号")
    private Integer version;

    /**
     * 存储内容（全量代码或差异 patch）
     */
    @Column(value = "content")
    @Schema(description = "存储内容（全量代码或差异 patch）")
    private String content;

    /**
     * 存储类型 (full, diff)
     */
    @Column(value = "storageType")
    @Schema(description = "存储类型")
    private AppVersionStoreTypeEnum storageType;

    /**
     * 版本说明，类似于 git commit message
     */
    @Column(value = "message")
    @Schema(description = "版本说明，类似于 git commit message")
    private String message;

    /**
     * 版本封面截图URL
     */
    @Column(value = "cover")
    @Schema(description = "版本封面截图URL")
    private String cover;

    /**
     * 关联的对话id，用于追溯版本来源
     */
    @Column(value = "chatHistoryId")
    @Schema(description = "关联的对话id，用于追溯版本来源")
    private Long chatHistoryId;

    /**
     * 创建时间
     */
    @Column(value = "createTime")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(value = "updateTime")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    @Schema(description = "是否删除")
    private Integer isDelete;



    /** 审计显示: id */
    @Schema(description = "审计显示: id")
    public static final String SHOW_ID = "id";

    /** 审计显示: 应用id */
    @Schema(description = "审计显示: 应用id")
    public static final String SHOW_APPID = "appId";

    /** 审计显示: 版本号，例如 1, 2, 3... */
    @Schema(description = "审计显示: 版本号，例如 1, 2, 3...")
    public static final String SHOW_VERSION = "version";

    /** 审计显示: 存储内容（全量代码或差异 patch） */
    @Schema(description = "审计显示: 存储内容（全量代码或差异 patch）")
    public static final String SHOW_CONTENT = "content";

    /** 审计显示: 存储类型 (full, diff) */
    @Schema(description = "审计显示: 存储类型 (full, diff)")
    public static final String SHOW_STORAGETYPE = "storageType";

    /** 审计显示: 版本说明，类似于 git commit message */
    @Schema(description = "审计显示: 版本说明，类似于 git commit message")
    public static final String SHOW_MESSAGE = "message";

    /** 审计显示: 版本封面截图URL */
    @Schema(description = "审计显示: 版本封面截图URL")
    public static final String SHOW_COVER = "cover";

    /** 审计显示: 关联的对话id，用于追溯版本来源 */
    @Schema(description = "审计显示: 关联的对话id，用于追溯版本来源")
    public static final String SHOW_CHATHISTORYID = "chatHistoryId";

    /** 审计显示: 创建时间 */
    @Schema(description = "审计显示: 创建时间")
    public static final String SHOW_CREATETIME = "createTime";

    /** 审计显示: 更新时间 */
    @Schema(description = "审计显示: 更新时间")
    public static final String SHOW_UPDATETIME = "updateTime";

    /** 审计显示: 是否删除 */
    @Schema(description = "审计显示: 是否删除")
    public static final String SHOW_ISDELETE = "isDelete";

}
