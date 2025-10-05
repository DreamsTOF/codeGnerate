package com.dream.codegenerate.newapi.model.tokrn.response;

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

}
