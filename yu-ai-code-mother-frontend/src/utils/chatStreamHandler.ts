import request from '@/request';
import { API_BASE_URL } from '@/config/env';
import {
  parseEventData,
  processEventData,
  type EventType,
  type ParsedEventData
} from './eventDataProcessor';

/**
 * 定义回调函数的接口，用于处理SSE流中的各种事件
 */
export interface ChatStreamCallbacks {
  // 接收到AI文本响应
  onAiResponse: (chunk: string, rawData: ParsedEventData) => void;
  // AI请求调用工具
  onToolRequest: (data: string, rawData: ParsedEventData) => void;
  // 接收到工具的流式输出
  onToolStream: (chunk: string, rawData: ParsedEventData) => void;
  // 工具执行完成
  onToolExecuted: (data: string, rawData: ParsedEventData) => void;
  // 流正常结束
  onDone: () => void;
  // 发生任何错误
  onError: (error: string) => void;
  // 接收到第一个数据块（用于隐藏初始加载状态）
  onFirstChunk: () => void;
}

/**
 * 定义发起流式请求所需的参数
 */
export interface ChatStreamParams {
  appId: string;
  userMessage: string;
}

/**
 * 启动聊天SSE流式请求
 * @param params 请求参数
 * @param callbacks 事件回调
 * @returns 返回一个包含 abort 方法的对象，用于中断连接
 */
export function startChatStream(params: ChatStreamParams, callbacks: ChatStreamCallbacks) {
  let eventSource: EventSource | null = null;
  let streamCompleted = false;

  const { appId, userMessage } = params;
  const {
    onAiResponse,
    onToolRequest,
    onToolStream,
    onToolExecuted,
    onDone,
    onError,
    onFirstChunk,
  } = callbacks;

  try {
    const baseURL = request.defaults.baseURL || API_BASE_URL;
    const urlParams = new URLSearchParams({
      appId: appId,
      message: userMessage,
    });
    const url = `${baseURL}/app/chat/gen/code?${urlParams}`;

    eventSource = new EventSource(url, {
      withCredentials: true,
    });

    let isFirstChunk = true;
    const handleFirstChunk = () => {
      if (isFirstChunk) {
        onFirstChunk();
        isFirstChunk = false;
      }
    };

    // 统一的数据处理函数
    const handleEventData = (rawData: string) => {
      if (streamCompleted) return;

      handleFirstChunk();

      // 解析事件数据
      const parsedData = parseEventData(rawData);
      if (!parsedData) {
        console.warn('无法解析事件数据:', rawData);
        return;
      }

      // 处理数据并获取显示文本
      const processedText = processEventData(parsedData);

      // 根据事件类型调用对应的回调
      switch (parsedData.type) {
        case 'ai_response':
          onAiResponse(processedText, parsedData);
          break;
        case 'tool_request':
          try {
            console.log('工具请求 - 内层数据:', parsedData.raw);
            // 工具信息在parsedData.raw中，而不是在parsedData.data中
            const toolData = parsedData.raw;
            console.log('解析后的工具数据:', toolData);
            // 转换OpenAI格式到我们的格式
            const formattedData = {
              toolName: toolData.name || toolData.toolName || '未知工具',
              description: `调用工具: ${toolData.name}`,
              arguments: toolData.arguments,
              id: toolData.id
            };
            onToolRequest(formattedData, parsedData);
          } catch (e) {
            console.error('解析工具请求数据失败:', e, '内层数据:', parsedData.raw);
            // 尝试从原始数据中提取工具名称
            let toolName = '未知工具';
            let description = '';

            try {
              // 尝试从内层数据中获取工具名称
              const rawData = parsedData.raw;
              if (rawData && rawData.name) {
                toolName = rawData.name;
                description = `调用工具: ${rawData.name}`;
              } else {
                // 尝试从字符串中提取
                const dataStr = JSON.stringify(rawData);
                if (dataStr.includes('modifyFile')) {
                  toolName = 'modifyFile';
                } else if (dataStr.includes('readFile')) {
                  toolName = 'readFile';
                } else if (dataStr.includes('writeFile')) {
                  toolName = 'writeFile';
                } else if (dataStr.includes('deleteFile')) {
                  toolName = 'deleteFile';
                } else if (dataStr.includes('listFiles')) {
                  toolName = 'listFiles';
                } else if (dataStr.includes('executeCommand')) {
                  toolName = 'executeCommand';
                }
                description = JSON.stringify(rawData);
              }
            } catch (parseError) {
              console.warn('解析工具名称失败:', parseError);
              description = JSON.stringify(parsedData.raw);
            }

            onToolRequest({ toolName, description }, parsedData);
          }
          break;
        case 'tool_stream':
          onToolStream(processedText, parsedData);
          break;
        case 'tool_executed':
          try {
            console.log('工具执行完成 - 内层数据:', parsedData.raw);
            // 工具信息在parsedData.raw中，而不是在parsedData.data中
            const toolResult = parsedData.raw;
            console.log('解析后的工具结果:', toolResult);
            // 转换OpenAI格式到我们的格式
            const formattedResult = {
              toolName: toolResult.name || toolResult.toolName || '工具',
              result: toolResult.result || toolResult.output || '',
              id: toolResult.id
            };
            onToolExecuted(formattedResult, parsedData);
          } catch (e) {
            console.error('解析工具执行结果失败:', e, '内层数据:', parsedData.raw);
            onToolExecuted({ result: JSON.stringify(parsedData.raw) }, parsedData);
          }
          break;
        default:
          // 未知类型默认作为AI响应处理
          onAiResponse(processedText, parsedData);
      }
    };

    // --- 监听后端定义的各种事件 ---
    // 根据后端事件类型监听对应的事件
    eventSource.addEventListener('ai_response', (event: MessageEvent) => {
      handleEventData(event.data);
    });

    eventSource.addEventListener('tool_request', (event: MessageEvent) => {
      handleEventData(event.data);
    });

    eventSource.addEventListener('tool_stream', (event: MessageEvent) => {
      handleEventData(event.data);
    });

    eventSource.addEventListener('tool_executed', (event: MessageEvent) => {
      handleEventData(event.data);
    });

    // 兼容旧的通用消息处理（如果后端发送没有事件类型的消息）
    eventSource.onmessage = (event: MessageEvent) => {
      // 尝试解析为通用消息格式
      try {
        const data = JSON.parse(event.data);
        if (data.d) {
          // 如果有d字段，使用事件数据处理器
          handleEventData(event.data);
        } else {
          // 否则作为简单的AI响应处理
          const fallbackData = {
            type: 'ai_response' as EventType,
            data: data.data || event.data,
            displayText: data.data || event.data,
            raw: data
          };
          onAiResponse(fallbackData.displayText, fallbackData);
        }
      } catch {
        // 如果解析失败，直接作为AI响应处理
        const fallbackData = {
          type: 'ai_response' as EventType,
          data: event.data,
          displayText: event.data,
          raw: { data: event.data, type: 'ai_response' }
        };
        onAiResponse(fallbackData.displayText, fallbackData);
      }
    };

    eventSource.addEventListener('done', () => {
      if (streamCompleted) return;
      streamCompleted = true;
      eventSource?.close();
      onDone();
    });

    eventSource.addEventListener('business-error', (event: MessageEvent) => {
      if (streamCompleted) return;
      streamCompleted = true;
      eventSource?.close();
      try {
        onError(JSON.parse(event.data));
      } catch (e) {
        onError({ message: '服务器返回了一个无法解析的错误' });
      }
    });

    eventSource.onerror = (err) => {
      if (streamCompleted) return;

      // 如果是正常关闭，则不应触发错误回调
      if (eventSource?.readyState === EventSource.CLOSED) {
        if (!streamCompleted) {
          streamCompleted = true;
          // 如果流在没有收到 'done' 事件的情况下关闭，也视为完成
          onDone();
        }
        return;
      }

      streamCompleted = true;
      eventSource?.close();
      onError(err);
    };

  } catch (error) {
    onError(error);
  }

  // 返回一个控制器，允许外部调用来中断SSE连接
  return {
    abort: () => {
      if (!streamCompleted) {
        streamCompleted = true;
        eventSource?.close();
        console.log('SSE stream aborted by client.');
      }
    },
  };
}
