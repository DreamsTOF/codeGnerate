package com.dream.codegenerate.model.entity;

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
 * 用户 实体类
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
@Table(dataSource ="master",value = "user")
@Schema(description = "用户")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    @Schema(description = "id")
    private Long id;

    /**
     * 账号
     */
    @Column(value = "userAccount")
    @Schema(description = "账号")
    private String userAccount;

    /**
     * 密码
     */
    @Column(value = "userPassword")
    @Schema(description = "密码")
    private String userPassword;

    /**
     * 用户昵称
     */
    @Column(value = "userName")
    @Schema(description = "用户昵称")
    private String userName;

    /**
     * 用户头像
     */
    @Column(value = "userAvatar")
    @Schema(description = "用户头像")
    private String userAvatar;

    /**
     * 用户简介
     */
    @Column(value = "userProfile")
    @Schema(description = "用户简介")
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    @Column(value = "userRole")
    @Schema(description = "用户角色：user/admin")
    private String userRole;

    /**
     * 会员兑换码
     */
    @Column(value = "vipCode")
    @Schema(description = "会员兑换码")
    private String vipCode;

    /**
     * 会员编号
     */
    @Column(value = "vipNumber")
    @Schema(description = "会员编号")
    private Long vipNumber;

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
     * 会员过期时间
     */
    @Column(value = "vipExpireTime")
    @Schema(description = "会员过期时间")
    private LocalDateTime vipExpireTime;



    /** 审计显示: id */
    @Schema(description = "审计显示: id")
    public static final String SHOW_ID = "id";

    /** 审计显示: 账号 */
    @Schema(description = "审计显示: 账号")
    public static final String SHOW_USERACCOUNT = "userAccount";

    /** 审计显示: 密码 */
    @Schema(description = "审计显示: 密码")
    public static final String SHOW_USERPASSWORD = "userPassword";

    /** 审计显示: 用户昵称 */
    @Schema(description = "审计显示: 用户昵称")
    public static final String SHOW_USERNAME = "userName";

    /** 审计显示: 用户头像 */
    @Schema(description = "审计显示: 用户头像")
    public static final String SHOW_USERAVATAR = "userAvatar";

    /** 审计显示: 用户简介 */
    @Schema(description = "审计显示: 用户简介")
    public static final String SHOW_USERPROFILE = "userProfile";

    /** 审计显示: 用户角色：user/admin */
    @Schema(description = "审计显示: 用户角色：user/admin")
    public static final String SHOW_USERROLE = "userRole";

    /** 审计显示: 会员兑换码 */
    @Schema(description = "审计显示: 会员兑换码")
    public static final String SHOW_VIPCODE = "vipCode";

    /** 审计显示: 会员编号 */
    @Schema(description = "审计显示: 会员编号")
    public static final String SHOW_VIPNUMBER = "vipNumber";

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

    /** 审计显示: 会员过期时间 */
    @Schema(description = "审计显示: 会员过期时间")
    public static final String SHOW_VIPEXPIRETIME = "vipExpireTime";

}
