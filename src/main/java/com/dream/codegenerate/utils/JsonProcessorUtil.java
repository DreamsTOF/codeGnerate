package com.dream.codegenerate.utils;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * Classname ToolRequestProcessorUtil
 * Description
 * Date 2025/10/4 17:11
 * Created by womon
 */
public class JsonProcessorUtil {
    /**
     * 定义内容预览的最大长度
     */
    private static final int CONTENT_PREVIEW_LENGTH = 50;
    // 定义裁切常量，方便统一修改
    private static final int HEAD_LENGTH = 50;
    private static final int TAIL_LENGTH = 50;
    /**
     * 精准处理toolExecutionRequests JSON字符串，仅当'content'字段存在时才对其进行替换，
     * 并完整保留所有其他未知参数。
     *
     * @param toolExecutionRequestsJson 原始的、未处理的toolExecutionRequests字段的JSON字符串。
     * @return 清洗和压缩后的新JSON字符串。
     */

    /**
     * [已升级] 处理 ToolExecutionRequest 的 JSON 字符串，
     * 对其中 'arguments' 内的 'content' 字段进行优雅裁切。
     *
     * @param toolExecutionRequestsJson ToolExecutionRequest 列表的 JSON 字符串
     * @return 经过处理后的 JSON 字符串
     */
    public static String processToolExecutionRequests(String toolExecutionRequestsJson) {
        if (toolExecutionRequestsJson == null || !JSONUtil.isTypeJSONArray(toolExecutionRequestsJson)) {
            return toolExecutionRequestsJson;
        }

        try {
            JSONArray toolCalls = JSONUtil.parseArray(toolExecutionRequestsJson);

            for (int i = 0; i < toolCalls.size(); i++) {
                JSONObject toolCall = toolCalls.getJSONObject(i);
                if (toolCall == null || toolCall.getStr("arguments") == null) {
                    continue;
                }

                String argumentsString = toolCall.getStr("arguments");
                JSONObject argumentsJson = JSONUtil.parseObj(argumentsString);

                if (argumentsJson.containsKey("content")) {
                    String originalContent = argumentsJson.getStr("content", "");

                    // 调用新的、更优雅的裁切方法
                    String truncatedContent = truncateContentInMiddle(originalContent);

                    // 更新 argumentsJson
                    argumentsJson.put("content", truncatedContent);

                    // 将修改后的 arguments 对象更新回 toolCall
                    toolCall.set("arguments", argumentsJson.toString());
                }
            }
            return toolCalls.toString();

        } catch (Exception e) {
            System.err.println("处理ToolExecutionRequests时发生错误: " + e.getMessage());
            return toolExecutionRequestsJson; // 保证在出错时返回原始数据
        }
    }

    /**
     * [新方法] 优雅地裁切字符串，保留头部和尾部，并用省略信息替换中间部分。
     *
     * @param originalContent 原始字符串
     * @return 裁切后的字符串
     */
    private static String truncateContentInMiddle(String originalContent) {
        if (originalContent == null) {
            return null;
        }

        int totalLength = originalContent.length();
        int threshold = HEAD_LENGTH + TAIL_LENGTH;

        // 如果原始字符串本身就不长，则无需裁切，直接返回
        if (totalLength <= threshold) {
            return originalContent;
        }

        // 提取头部
        String head = originalContent.substring(0, HEAD_LENGTH);
        // 提取尾部
        String tail = originalContent.substring(totalLength - TAIL_LENGTH);
        // 计算省略的字符数
        long omittedCount = totalLength - threshold;

        // 构建中间的提示信息
        String ellipsis = String.format("... (此处省略 %d 个字符) ...", omittedCount);

        return head + ellipsis + tail;
    }


    /**
     * 从一个JSON数组字符串中，查找并返回第一个匹配指定字段名的字段值。
     *
     * @param jsonArrayString 原始的JSON数组字符串. e.g., "[{\"key1\":\"value1\"}, {\"key1\":\"value2\"}]"
     * @param fieldName       需要查找的字段名 (key). e.g., "key1"
     * @return 找到的第一个匹配的字段值 (String类型). 如果未找到或发生错误，则返回 null。
     */
    public static String findFirstFieldValue(String jsonArrayString, String fieldName) {
        // 1. 基础校验
        if (jsonArrayString == null || jsonArrayString.trim().isEmpty() ||
                fieldName == null || fieldName.trim().isEmpty() ||
                !JSONUtil.isTypeJSONArray(jsonArrayString)) {
            return null;
        }

        try {
            JSONArray array = JSONUtil.parseArray(jsonArrayString);

            // 2. 遍历数组中的每一个对象
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);

                // 3. 检查当前对象是否包含指定的字段
                if (obj != null && obj.containsKey(fieldName)) {
                    // 4. 如果包含，立即返回该字段的值，并终止方法
                    return obj.getStr(fieldName);
                }
            }

        } catch (Exception e) {
            System.err.println("解析JSON或提取字段时出错: " + e.getMessage());
            return null; // 发生任何异常都安全返回null
        }

        // 5. 如果遍历完整个数组都没有找到，返回null
        return null;
    }

    public static String findActionDescriptionInArguments(String jsonArrayString) {
        // 1. 基础校验
        if (jsonArrayString == null || jsonArrayString.trim().isEmpty() ||
                !JSONUtil.isTypeJSONArray(jsonArrayString)) {
            return null;
        }

        try {
            JSONArray array = JSONUtil.parseArray(jsonArrayString);
            if (array.isEmpty()) {
                return null;
            }

            // 假设我们总是处理数组中的第一个工具调用请求
            JSONObject topLevelObj = array.getJSONObject(0);

            // 2. 检查是否存在 "arguments" 字段
            if (topLevelObj == null || !topLevelObj.containsKey("arguments")) {
                return null;
            }

            // 3.【第一步】获取 "arguments" 的值，它是一个字符串
            String argumentsJsonString = topLevelObj.getStr("arguments");
            if (argumentsJsonString == null) {
                return null;
            }

            // 4.【第二步】将这个字符串再次解析为一个 JSONObject
            JSONObject argumentsObj = JSONUtil.parseObj(argumentsJsonString);

            // 5.【第三步】从这个新的、内层的 JSONObject 中获取 "actionDescription" 的值
            return argumentsObj.getStr("actionDescription");

        } catch (Exception e) {
            System.err.println("解析JSON或提取字段时出错: " + e.getMessage());
            return null;
        }
    }

}
