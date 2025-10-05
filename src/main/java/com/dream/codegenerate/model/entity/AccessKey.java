package com.dream.codegenerate.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访问key 实体类。
 *
 * @author dream
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("access_key")
public class AccessKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 关联的用户id
     */
    @Column("userId")
    private Long userId;

    /**
     * 访问api的key
     */
    @Column("apiKey")
    private String apiKey;

    /**
     * 是否使用过兑换码，当前是第几位的兑换码
     */
    @Column("isUse")
    private Integer isUse;

    /**
     * 兑换码
     */
    @Column("cdKey")
    private String cdKey;

    /**
     * key的ID
     */
    @Column("apiKeyId")
    private Long apiKeyId;

}
