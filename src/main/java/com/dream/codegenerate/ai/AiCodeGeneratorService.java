package com.dream.codegenerate.ai;

import com.dream.codegenerate.ai.model.HtmlCodeResult;
import com.dream.codegenerate.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;


public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<ServerSentEvent<String>> generateHtmlCodeStream(UserMessage userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<ServerSentEvent<String>> generateMultiFileCodeStream(String userMessage);

    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户提示词
     * @return AI 的输出结果
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @dev.langchain4j.service.UserMessage String userMessage);

    /**
     * 通用的流式代码生成方法
     * <p>
     * 该方法将根据传入的 System Prompt 决定 AI 的行为模式。
     * - 对于 Vue 项目，它会遵循 Vue 的生成逻辑。
     * - 对于 HTML 或多文件项目，它会遵循新的、强制使用工具的生成逻辑。
     *
     * @param appId       会话 ID，用于工具上下文
     * @param userMessage 用户的需求描述
     * @return 一个 TokenStream，包含了 AI 的完整思考和工具调用过程
     */
//    @SystemMessage(fromResource = "{{systemPromptResource}}") // 使用动态的 System Prompt
    TokenStream generateCodeByStream(@MemoryId long appId, @dev.langchain4j.service.UserMessage String userMessage);
}
