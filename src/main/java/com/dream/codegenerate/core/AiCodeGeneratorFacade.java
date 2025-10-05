package com.dream.codegenerate.core;

import cn.hutool.json.JSONUtil;
import com.dream.codegenerate.ai.AiCodeGeneratorService;
import com.dream.codegenerate.ai.AiCodeGeneratorServiceFactory;
import com.dream.codegenerate.ai.model.HtmlCodeResult;
import com.dream.codegenerate.ai.model.MultiFileCodeResult;
import com.dream.codegenerate.ai.model.message.*;
import com.dream.codegenerate.constant.AppConstant;
import com.dream.codegenerate.core.builder.BuildResult;
import com.dream.codegenerate.core.builder.VueProjectBuilder;
import com.dream.codegenerate.ai.tools.context.SessionContextManager;
import com.dream.codegenerate.core.parser.CodeParserExecutor;
import com.dream.codegenerate.core.saver.CodeFileSaverExecutor;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.model.enums.CodeGenTypeEnum;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.Set;

/**
 * AI 代码生成门面类，组合代码生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {



    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private SessionContextManager contextManager; // 注入管理器

    /**
     * 需要进行前端流式“回放”的工具名称列表。
     * 未来有新工具需要此功能时，只需在此处添加其名称即可，无需修改核心逻辑。
     */
    private static final Set<String> STREAMING_TOOLS = Set.of("writeFile");

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, new UserMessage(userMessage),codeGenTypeEnum, "apiKey");
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @return 保存的目录
     */
    @Deprecated
    public Flux<ServerSentEvent<String>> generateAndSaveCodeStream(UserMessage userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "生成类型不能为空");
        }
        // 根据 appId 获取相应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, userMessage,codeGenTypeEnum, "apiKey");
        contextManager.getContext(appId).setCodeGenType(codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<ServerSentEvent<String>> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<ServerSentEvent<String>> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage.singleText());
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage.singleText());
                yield processTokenStream(tokenStream, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId           应用 ID
     * @param apiKey
     * @return 包含AI思考和工具调用过程的SSE事件流
     */
    public Flux<ServerSentEvent<String>> generateCodeStream(UserMessage userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, String apiKey) {
        // 1. 从工厂获取根据当前任务优化的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, userMessage,codeGenTypeEnum,apiKey);

        // 2. 在上下文中记录当前的生成类型，这对于工具内部逻辑可能有用
        contextManager.getContext(appId).setCodeGenType(codeGenTypeEnum);

        // 3. 调用统一的流式生成方法
        TokenStream tokenStream = aiCodeGeneratorService.generateCodeByStream(appId, userMessage.singleText());

        // 4. 将 TokenStream 转换为包含自定义事件的 SSE Flux
        return processTokenStreamToSse(tokenStream, appId);
    }


    /**
     * 将 TokenStream 转换为包含自定义前端事件的 Flux<ServerSentEvent<String>>
     *
     * @param tokenStream TokenStream 对象
     * @param appId       应用 ID
     * @return Flux<ServerSentEvent < String>> 流式响应
     */
    private Flux<ServerSentEvent<String>> processTokenStreamToSse(TokenStream tokenStream, Long appId) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse(partialResponse -> {
                        // 发送 AI 思考的文本流
                        AiResponseMessage eventDto = new AiResponseMessage(partialResponse);
                        sink.next(ServerSentEvent.<String>builder()
                                .event(eventDto.getType())
                                .data(JSONUtil.toJsonStr(eventDto))
                                .build());
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        String toolName = toolExecutionRequest.name();
                            // 对于其他工具，发送常规的请求事件
                            ToolRequestMessage eventDto = new ToolRequestMessage(toolExecutionRequest);
                            sink.next(ServerSentEvent.<String>builder()
                                    .event(eventDto.getType())
                                    .data(JSONUtil.toJsonStr(eventDto))
                                    .build());
                    })
                    .onToolExecuted(toolExecution -> {
                        // 发送工具执行完毕的确认事件
                        ToolExecutedMessage eventDto = new ToolExecutedMessage(toolExecution);
//                        eventDto.setArguments("");
                            sink.next(ServerSentEvent.<String>builder()
                                    .event(eventDto.getType())
                                    .data(JSONUtil.toJsonStr(eventDto))
                                    .build());

                    })
                    .onCompleteResponse(response -> {
                        // 对于Vue项目，在所有流程结束后执行构建
                        BuildResult buildResult= null;
                        if (contextManager.getContext(appId).getCodeGenType() == CodeGenTypeEnum.VUE_PROJECT) {
                            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                            buildResult = vueProjectBuilder.buildProject(projectPath);
                        }
                        if (buildResult != null && !buildResult.isSuccess()){

                        }
                        // 发送流程结束的信号
                        sink.next(ServerSentEvent.<String>builder().event("done").data("").build());
                        sink.complete();
                    })
                    .onError(error-> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }

    /**
     * 将 TokenStream 转换为 Flux<ServerSentEvent<String>>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @param appId       应用 ID
     * @return Flux<ServerSentEvent < String>> 流式响应
     */
    @Deprecated
    private Flux<ServerSentEvent<String>> processTokenStream(TokenStream tokenStream, Long appId) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(ServerSentEvent.builder(JSONUtil.toJsonStr(aiResponseMessage)).build());
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                        sink.next(ServerSentEvent.builder(JSONUtil.toJsonStr(toolRequestMessage)).build());
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(ServerSentEvent.builder(JSONUtil.toJsonStr(toolExecutedMessage)).build());
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 执行 Vue 项目构建（同步执行，确保预览时项目已就绪）
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }

    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 流式响应
     */
    @Deprecated
    private Flux<ServerSentEvent<String>> processCodeStream(Flux<ServerSentEvent<String>> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        // 字符串拼接器，用于当流式返回所有的代码之后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(event -> {
            // 实时收集代码片段
            codeBuilder.append(event.data());
        }).doOnComplete(() -> {
            // 流式返回完成后，保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File saveDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，目录为：{}", saveDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }
}
