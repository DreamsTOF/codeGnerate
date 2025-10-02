package com.dream.codegenerate.controller;


//import com.dream.codegenerate.langgraph4j.CodeGenWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 工作流 SSE 控制器
 * 演示 LangGraph4j 工作流的流式输出功能
 */
@RestController
@RequestMapping("/workflow")
@Slf4j
public class WorkflowSseController {

    /**
     * SSE 流式执行工作流
     */
//    @GetMapping(value = "/execute-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public SseEmitter executeWorkflowWithSse(@RequestParam String prompt) {
//        log.info("收到 SSE 工作流执行请求: {}", prompt);
//        return new CodeGenWorkflow().executeWorkflowWithSse(prompt);
//    }
}
