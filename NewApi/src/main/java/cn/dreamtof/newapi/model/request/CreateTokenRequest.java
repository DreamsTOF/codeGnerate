package cn.dreamtof.newapi.model.request;

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
    private String name;
    private long expired_time;
    private long remain_quota;
    private boolean unlimited_quota;
    private boolean model_limits_enabled;
    private List<String> model_limits; // 请求中是数组
    private String allow_ips;
    private String group;

    // 此处省略所有字段的 Getter 和 Setter 方法，实际使用时请添加
    // ...
}
