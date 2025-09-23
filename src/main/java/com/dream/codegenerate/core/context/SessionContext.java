package com.dream.codegenerate.core.context;

import com.dream.codegenerate.model.enums.CodeGenTypeEnum;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于存储单个会话（appId）的上下文信息
 */
@Data
public class SessionContext {
    /**
     * 当前 AI 的工作模式
     */
    private CodeGenTypeEnum codeGenType;

}
