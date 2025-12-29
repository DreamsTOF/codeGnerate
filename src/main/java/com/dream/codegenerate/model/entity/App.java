package com.dream.codegenerate.model.entity;

import com.dream.codegenerate.log.autoAudit.AuditReference;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Column;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;
import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.Accessors;

/**
 * 应用 实体类
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
@Table(dataSource ="master",value = "app")
@Schema(description = "应用")
public class App implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    @Schema(description = "id")
    private Long id;
    /**
     * 应用名称
     */
    @Column(value = "appName")
    @Schema(description = "应用名称")
    private String appName;
    /**
     * 应用封面
     */
    @Column(value = "cover")
    @Schema(description = "应用封面")
    private String cover;
    /**
     * 应用初始化的 prompt
     */
    @Column(value = "initPrompt")
    @Schema(description = "应用初始化的 prompt")
    private String initPrompt;
    /**
     * 代码生成类型（枚举）
     */
    @Column(value = "codeGenType")
    @Schema(description = "代码生成类型（枚举）")
    private String codeGenType;
    /**
     * 部署标识
     */
    @Column(value = "deployKey")
    @Schema(description = "部署标识")
    private String deployKey;
    /**
     * 部署时间
     */
    @Column(value = "deployedTime")
    @Schema(description = "部署时间")
    private LocalDateTime deployedTime;
    /**
     * 优先级
     */
    @Column(value = "priority")
    @Schema(description = "优先级")
    private Integer priority;
    /**
     * 创建用户id
     */
    @Column(value = "userId")
    @Schema(description = "创建用户")
    @AuditReference(target = User.class,label = User.SHOW_USERNAME)
    private Long userId;
    /**
     * 编辑时间
     */
    @Column(value = "editTime")
    @Schema(description = "编辑时间")
    private LocalDateTime editTime;
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
    /**
     * 当前应用版本
     */
    @Column(value = "currentVersion")
    @Schema(description = "当前应用版本")
    private Long currentVersion;


    /** 审计显示: id */
    @Schema(description = "审计显示: id")
    public static final String SHOW_ID = "id";

    /** 审计显示: 应用名称 */
    @Schema(description = "审计显示: 应用名称")
    public static final String SHOW_APPNAME = "appName";

    /** 审计显示: 应用封面 */
    @Schema(description = "审计显示: 应用封面")
    public static final String SHOW_COVER = "cover";

    /** 审计显示: 应用初始化的 prompt */
    @Schema(description = "审计显示: 应用初始化的 prompt")
    public static final String SHOW_INITPROMPT = "initPrompt";

    /** 审计显示: 代码生成类型（枚举） */
    @Schema(description = "审计显示: 代码生成类型（枚举）")
    public static final String SHOW_CODEGENTYPE = "codeGenType";

    /** 审计显示: 部署标识 */
    @Schema(description = "审计显示: 部署标识")
    public static final String SHOW_DEPLOYKEY = "deployKey";

    /** 审计显示: 部署时间 */
    @Schema(description = "审计显示: 部署时间")
    public static final String SHOW_DEPLOYEDTIME = "deployedTime";

    /** 审计显示: 优先级 */
    @Schema(description = "审计显示: 优先级")
    public static final String SHOW_PRIORITY = "priority";

    /** 审计显示: 创建用户id */
    @Schema(description = "审计显示: 创建用户id")
    public static final String SHOW_USERID = "userId";

    /** 审计显示: 编辑时间 */
    @Schema(description = "审计显示: 编辑时间")
    public static final String SHOW_EDITTIME = "editTime";

    /** 审计显示: 创建时间 */
    @Schema(description = "审计显示: 创建时间")
    public static final String SHOW_CREATETIME = "createTime";

    /** 审计显示: 更新时间 */
    @Schema(description = "审计显示: 更新时间")
    public static final String SHOW_UPDATETIME = "updateTime";

    /** 审计显示: 是否删除 */
    @Schema(description = "审计显示: 是否删除")
    public static final String SHOW_ISDELETE = "isDelete";

    /** 审计显示: 当前应用版本 */
    @Schema(description = "审计显示: 当前应用版本")
    public static final String SHOW_CURRENTVERSION = "currentVersion";

}
