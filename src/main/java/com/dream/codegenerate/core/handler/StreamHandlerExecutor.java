package com.dream.codegenerate.core.handler;

import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.enums.CodeGenTypeEnum;
import com.dream.codegenerate.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 流处理器执行器
 * 根据代码生成类型创建合适的流处理器：
 * 1. Flux<ServerSentEvent<String>> 流（HTML、MULTI_FILE） -> SimpleTextStreamHandler
 * 2. TokenStream 格式的复杂流（VUE_PROJECT） -> JsonMessageStreamHandler
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    /**
     * 创建流处理器并处理聊天历史记录
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @param codeGenType        代码生成类型
     * @return 处理后的流
     */
//    public Flux<ServerSentEvent<String>> doExecute(Flux<ServerSentEvent<String>> originFlux,
//                                                       ChatHistoryService chatHistoryService,
//                                                       long appId, User loginUser, CodeGenTypeEnum codeGenType) {
//        return switch (codeGenType) {
//            case VUE_PROJECT -> // 使用注入的组件实例
//                    jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, loginUser);
//            case HTML, MULTI_FILE -> // 简单文本处理器不需要依赖注入
//                    new SimpleTextStreamHandler().handle(originFlux, chatHistoryService, appId, loginUser);
//        };
//    }

    public Flux<ServerSentEvent<String>> doExecute(Flux<ServerSentEvent<String>> originFlux,
                                                   ChatHistoryService chatHistoryService,
                                                   long appId, User loginUser, CodeGenTypeEnum codeGenType) {
        return jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, loginUser);

    }
}
