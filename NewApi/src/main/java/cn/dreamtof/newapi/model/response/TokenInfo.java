package cn.dreamtof.newapi.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Token详细信息 DTO (用于搜索接口的响应)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo implements Serializable {
    private long id;
    private String name;
    private String key;
    private int status;
    private long remain_quota;
    private boolean unlimited_quota;
    private boolean model_limits_enabled;
    private String model_limits; // 注意：响应中是字符串
    private String allow_ips;
    private String group;
    private long expired_time;
    private long created_time;
    private long accessed_time;

    // 此处省略所有字段的 Getter 和 Setter 方法，实际使用时请添加
    // ...
}
