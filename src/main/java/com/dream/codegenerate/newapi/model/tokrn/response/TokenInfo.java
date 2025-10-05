package com.dream.codegenerate.newapi.model.tokrn.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Token详细信息 DTO (用于搜索接口的响应)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo implements Serializable {
    private long id;
    /**
     * 名字
     */
    private String name;
    /**
     * apikey
     */
    private String key;
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
    private long created_time;
    private long accessed_time;
}
