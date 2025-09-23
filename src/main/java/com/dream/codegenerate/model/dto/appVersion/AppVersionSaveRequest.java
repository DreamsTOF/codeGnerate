package com.dream.codegenerate.model.dto.appVersion;

import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import com.dream.codegenerate.model.enums.CodeGenTypeEnum;
import lombok.Data;
import org.aspectj.apache.bcel.classfile.Code;

import java.io.Serializable;

/**
 * 应用版本保存请求
 */
@Data
public class AppVersionSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 版本说明，类似于 git commit message
     */
    private String message;

    /**
     * 应用版本存储类型
     */
    private CodeGenTypeEnum codeGenType;
}
