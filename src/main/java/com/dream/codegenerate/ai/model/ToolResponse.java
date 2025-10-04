package com.dream.codegenerate.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Classname ToolResponse
 * Description
 * Date 2025/10/4 16:44
 * Created by womon
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolResponse implements java.io.Serializable{
    Boolean success;
    String content;
    String actionDescription;

    public ToolResponse(String content, String actionDescription) {
        this.success = true;
        this.content = content;
        this.actionDescription = actionDescription;
    }
}
