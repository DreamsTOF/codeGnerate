/**
 * 事件数据处理器
 * 用于处理从后端接收到的各种类型的事件数据
 */

// 定义事件类型
export enum EventType {
  AI_RESPONSE = 'ai_response',
  TOOL_REQUEST = 'tool_request',
  TOOL_EXECUTED = 'tool_executed',
  TOOL_STREAM = 'tool_stream'
}

// 定义事件数据接口
export interface EventData {
  data: string
  type: EventType
  toolName?: string
  toolParams?: string
  toolResult?: string
  description?: string
}

// 定义解析后的事件数据
export interface ParsedEventData {
  type: EventType
  data: string
  displayText: string
  raw: EventData
}

/**
 * 解析后端返回的原始数据
 * @param rawData 后端返回的原始数据，格式：{"d":"{\"data\":\"的要求\",\"type\":\"ai_response\"}"}
 * @returns 解析后的事件数据
 */
export function parseEventData(rawData: string): ParsedEventData | null {
  try {
    // 第一层解析：解析外层的JSON
    const outerData = JSON.parse(rawData)

    // 获取d字段
    const dField = outerData.d
    if (!dField) {
      console.warn('事件数据缺少d字段:', rawData)
      return null
    }

    // 第二层解析：解析d字段中的JSON
    const innerData = JSON.parse(dField)

    // 验证必需字段
    if (!innerData.type) {
      console.warn('事件数据缺少type字段:', innerData)
      return null
    }

    // 创建解析后的事件数据
    const parsedData: ParsedEventData = {
      type: innerData.type as EventType,
      data: innerData.data || '',
      displayText: generateDisplayText(innerData.type, innerData.data),
      raw: innerData
    }

    return parsedData
  } catch (error) {
    console.error('解析事件数据失败:', error, '原始数据:', rawData)
    return null
  }
}

/**
 * 根据事件类型和数据显示文本
 * @param type 事件类型
 * @param data 原始数据
 * @returns 显示文本
 */
function generateDisplayText(type: EventType, data: string): string {
  switch (type) {
    case EventType.AI_RESPONSE:
      return data
    case EventType.TOOL_REQUEST:
      return `工具请求: ${data}`
    case EventType.TOOL_STREAM:
      return data
    case EventType.TOOL_EXECUTED:
      return `工具执行完成: ${data}`
    default:
      return data
  }
}

/**
 * 处理AI响应事件
 * @param data 事件数据
 * @returns 处理后的显示数据
 */
export function processAiResponse(data: ParsedEventData): string {
  // 暂时不做特殊处理，直接返回数据
  return data.displayText
}

/**
 * 处理工具请求事件
 * @param data 事件数据
 * @returns 处理后的显示数据
 */
export function processToolRequest(data: ParsedEventData): string {
  // 暂时不做特殊处理，直接返回数据
  return data.displayText
}

/**
 * 处理工具执行事件
 * @param data 事件数据
 * @returns 处理后的显示数据
 */
export function processToolExecuted(data: ParsedEventData): string {
  // 暂时不做特殊处理，直接返回数据
  return data.displayText
}

/**
 * 处理工具流式事件
 * @param data 事件数据
 * @returns 处理后的显示数据
 */
export function processToolStream(data: ParsedEventData): string {
  // 暂时不做特殊处理，直接返回数据
  return data.displayText
}

/**
 * 根据事件类型调用对应的处理函数
 * @param parsedData 解析后的事件数据
 * @returns 处理后的显示文本
 */
export function processEventData(parsedData: ParsedEventData): string {
  switch (parsedData.type) {
    case EventType.AI_RESPONSE:
      return processAiResponse(parsedData)
    case EventType.TOOL_REQUEST:
      return processToolRequest(parsedData)
    case EventType.TOOL_EXECUTED:
      return processToolExecuted(parsedData)
    case EventType.TOOL_STREAM:
      return processToolStream(parsedData)
    default:
      return parsedData.displayText
  }
}

/**
 * 事件处理器映射
 */
export const EventProcessors = {
  [EventType.AI_RESPONSE]: processAiResponse,
  [EventType.TOOL_REQUEST]: processToolRequest,
  [EventType.TOOL_EXECUTED]: processToolExecuted,
  [EventType.TOOL_STREAM]: processToolStream,
} as const

/**
 * 批量处理事件数据
 * @param rawDataArray 原始数据数组
 * @returns 处理后的文本数组
 */
export function processBatchEvents(rawDataArray: string[]): string[] {
  return rawDataArray
    .map(parseEventData)
    .filter((data): data is ParsedEventData => data !== null)
    .map(processEventData)
}
