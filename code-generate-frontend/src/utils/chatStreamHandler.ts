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
  onToolRequest: (data: any, rawData: ParsedEventData) => void;
  // 接收到工具的流式输出
  onToolStream: (chunk: string, rawData: ParsedEventData) => void;
  // 工具执行完成
  onToolExecuted: (data: any, rawData: ParsedEventData) => void;
  // 流正常结束
  onDone: () => void;
  // 发生任何错误
  onError: (error: any) => void;
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
 * @returns 【关键修改】：直接返回 EventSource 实例
 */
export function startChatStream(params: ChatStreamParams, callbacks: ChatStreamCallbacks): EventSource {
  let eventSource: EventSource;
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

  const baseURL = request.defaults.baseURL || API_BASE_URL;
  const urlParams = new URLSearchParams({
    appId: appId,
    message: userMessage,
  });
  const url = `${baseURL}/app/chat/gen/code?${urlParams}`;

  eventSource = new EventSource(url, {
    withCredentials: true,
  });

  try {
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
            const toolData = parsedData.raw;
            const formattedData = {
              toolName: toolData.name || toolData.toolName || '未知工具',
              description: `调用工具: ${toolData.name}`,
              arguments: toolData.arguments,
              id: toolData.id
            };
            onToolRequest(formattedData, parsedData);
          } catch (e) {
            console.error('解析工具请求数据失败:', e, '内层数据:', parsedData.raw);
            onToolRequest({ toolName: '未知工具', description: JSON.stringify(parsedData.raw) }, parsedData);
          }
          break;
        case 'tool_stream':
          onToolStream(processedText, parsedData);
          break;
        case 'tool_executed':
          try {
            const toolResult = parsedData.raw;
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

    // 兼容旧的通用消息处理
    eventSource.onmessage = (event: MessageEvent) => {
      handleEventData(event.data);
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

      if (eventSource?.readyState === EventSource.CLOSED) {
        if (!streamCompleted) {
          streamCompleted = true;
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
    // 如果构造函数或初始设置失败，确保关闭连接
    if (eventSource) {
      eventSource.close();
    }
  }

  // 【关键修改】：直接返回 eventSource 实例，而不是一个包含 abort 方法的对象
  return eventSource;
}
