package com.dream.codegenerate.ai.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式消息响应基类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamMessage {

    /**
     * 消息类型
     */
    private String type;

    /**
     * 提供一个统一的方法来获取事件类型名称。
     * 这使得所有继承此类或实现相关接口的子类都能被统一处理。
     * @return 事件类型字符串
     */
    public String getEventType() {
        return this.type;
    }
}
