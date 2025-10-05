package com.dream.codegenerate.newapi.model.tokrn.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 创建Token请求 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTokenRequest implements Serializable {
    /**
     * 名字
     */
    private String name;
    /**
     * 过期时间
     */
    private long expired_time;
    /**
     * 额度
     */
    private long remain_quota;
    /**
     * 是否为无限额度
     */
    private boolean unlimited_quota;
    /**
     * 开启模型限制
     */
    private boolean model_limits_enabled;
    /**
     * 限制的模型列表
     */
    private List<String> model_limits; // 请求中是数组
    /**
     * 限制的ip列表
     */
    private String allow_ips;
    /**
     * 分组名字
     */
    private String group;

}
