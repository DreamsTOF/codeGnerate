package cn.dreamtof.newapi.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 更新Token操作的响应数据 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTokenResponseData implements Serializable {
    private long id;
    private String name;
    private int status;

    // 此处省略所有字段的 Getter 和 Setter 方法，实际使用时请添加
    // ...
}
