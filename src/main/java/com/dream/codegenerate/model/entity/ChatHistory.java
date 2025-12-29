package com.dream.codegenerate.model.entity;

import com.dream.codegenerate.log.autoAudit.AuditReference;
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
 * 对话历史 实体类
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
@Table(dataSource ="master",value = "chat_history")
@Schema(description = "对话历史")
public class ChatHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    @Schema(description = "id")
    private Long id;

    /**
     * 消息
     */
    @Column(value = "message")
    @Schema(description = "消息")
    private String message;

    /**
     * user/ai
     */
    @Column(value = "messageType")
    @Schema(description = "user/ai")
    private String messageType;

    /**
     * 应用id
     */
    @Column(value = "appId")
    @Schema(description = "应用id")
    @AuditReference(target = App.class, label = App.SHOW_APPNAME)
    private Long appId;

    /**
     * 创建用户id
     */
    @Column(value = "userId")
    @Schema(description = "创建用户")
    @AuditReference(target = User.class,label = User.SHOW_USERNAME)
    private Long userId;

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

    /** 审计显示: 消息 */
    @Schema(description = "审计显示: 消息")
    public static final String SHOW_MESSAGE = "message";

    /** 审计显示: user/ai */
    @Schema(description = "审计显示: user/ai")
    public static final String SHOW_MESSAGETYPE = "messageType";

    /** 审计显示: 应用id */
    @Schema(description = "审计显示: 应用id")
    public static final String SHOW_APPID = "appId";

    /** 审计显示: 创建用户id */
    @Schema(description = "审计显示: 创建用户id")
    public static final String SHOW_USERID = "userId";

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
