package com.dream.codegenerate.model.entity;

import com.dream.codegenerate.log.autoAudit.AuditReference;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.annotation.Column;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.*;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 访问key 实体类
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
@Table(dataSource ="master",value = "access_key")
@Schema(description = "访问key")
public class AccessKey implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    @Schema(description = "id")
    private Long id;

    /**
     * 关联的用户id
     */
    @Column(value = "userId")
    @Schema(description = "关联的用户id")
    @AuditReference(target = User.class,label = User.SHOW_USERNAME)
    private Long userId;

    /**
     * 访问api的key
     */
    @Column(value = "apiKey")
    @Schema(description = "访问api的key")
    private String apiKey;

    /**
     * 是否使用过兑换码，当前是第几位的兑换码
     */
    @Column(value = "isUse")
    @Schema(description = "是否使用过兑换码，当前是第几位的兑换码")
    private Integer isUse;

    /**
     * 兑换码
     */
    @Column(value = "cdKey")
    @Schema(description = "兑换码")
    private String cdKey;

    /**
     * key的ID
     */
    @Column(value = "apiKeyId")
    @Schema(description = "key的ID")
    private Long apiKeyId;



    /** 审计显示: id */
    @Schema(description = "审计显示: id")
    public static final String SHOW_ID = "id";

    /** 审计显示: 关联的用户id */
    @Schema(description = "审计显示: 关联的用户id")
    public static final String SHOW_USERID = "userId";

    /** 审计显示: 访问api的key */
    @Schema(description = "审计显示: 访问api的key")
    public static final String SHOW_APIKEY = "apiKey";

    /** 审计显示: 是否使用过兑换码，当前是第几位的兑换码 */
    @Schema(description = "审计显示: 是否使用过兑换码，当前是第几位的兑换码")
    public static final String SHOW_ISUSE = "isUse";

    /** 审计显示: 兑换码 */
    @Schema(description = "审计显示: 兑换码")
    public static final String SHOW_CDKEY = "cdKey";

    /** 审计显示: key的ID */
    @Schema(description = "审计显示: key的ID")
    public static final String SHOW_APIKEYID = "apiKeyId";

}
