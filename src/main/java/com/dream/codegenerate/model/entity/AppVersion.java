package com.dream.codegenerate.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import com.dream.codegenerate.model.vo.appVersion.AppVersionQueryVO;
import com.dream.codegenerate.model.vo.appVersion.AppVersionVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用版本 实体类。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_version")
public class AppVersion implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 应用id
     */
    @Column("appId")
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
    @Column("storageType")
    private AppVersionStoreTypeEnum storageType;

    /**
     * 版本说明，类似于 git commit message
     */
    private String message;

    /**
     * 关联的对话id，用于追溯版本来源
     */
    @Column("chatHistoryId")
    private Long chatHistoryId;

    /**
     * 创建时间
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

    public static AppVersionQueryVO toAppVersionQueryVO(AppVersion appVersion){
        return AppVersionQueryVO.builder()
                .appId(appVersion.getAppId())
                .version(appVersion.getVersion())
                .storageType(appVersion.getStorageType())
                .message(appVersion.getMessage())
                .build();
    }
    public static AppVersionVO toAppVersionVO(AppVersion appVersion){
        return AppVersionVO.builder()
                .appId(appVersion.getAppId())
                .version(appVersion.getVersion())
                .content(appVersion.getContent())
                .storageType(appVersion.getStorageType())
                .message(appVersion.getMessage())
                .createTime(appVersion.getCreateTime())
                .updateTime(appVersion.getUpdateTime())
                .build();
    }

    public static AppVersion toAppVersion(AppVersionVO appVersionVO){
        return AppVersion.builder()
                .appId(appVersionVO.getAppId())
                .version(appVersionVO.getVersion())
                .content(appVersionVO.getContent())
                .storageType(appVersionVO.getStorageType())
                .message(appVersionVO.getMessage())
                .build();
    }
    public static AppVersion toAppVersion(AppVersionQueryVO appVersionQueryVO){
        return AppVersion.builder()
                .appId(appVersionQueryVO.getAppId())
                .version(appVersionQueryVO.getVersion())
                .storageType(appVersionQueryVO.getStorageType())
                .message(appVersionQueryVO.getMessage())
                .build();
    }
}
