package com.dream.codegenerate.model.dto.staticFile;

import com.dream.codegenerate.model.enums.CodeGenTypeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * Classname listStaticFilesRequest
 * Description 获取静态资源文件列表请求类
 * Date 2025/9/23 17:33
 * Created by womon
 */
@Data
public class StaticFilesListRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private CodeGenTypeEnum codeGenType;

    private Long appId;
}
