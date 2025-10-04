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
    private static final int CONTENT_PREVIEW_LENGTH = 100;

    /**
     * 精准处理toolExecutionRequests JSON字符串，仅当'content'字段存在时才对其进行替换，
     * 并完整保留所有其他未知参数。
     *
     * @param toolExecutionRequestsJson 原始的、未处理的toolExecutionRequests字段的JSON字符串。
     * @return 清洗和压缩后的新JSON字符串。
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

                // 核心逻辑：只在 'content' 字段存在时才进行操作
                if (argumentsJson.containsKey("content")) {

                    // 1. 获取并处理 content
                    String originalContent = argumentsJson.getStr("content", ""); // 安全地获取
                    String preview = originalContent.substring(0, Math.min(originalContent.length(), CONTENT_PREVIEW_LENGTH));

                    // 2. 直接在原始的argumentsJson对象上进行修改，以保留所有其他参数
                    argumentsJson.remove("content"); // 移除旧字段
                    argumentsJson.put("content", preview); // 添加新字段

                    // 3. 将修改后的 arguments 对象更新回 toolCall
                    toolCall.set("arguments", argumentsJson.toString());
                }
                // 如果 arguments 中没有 content 字段，则此循环不对其做任何操作，直接跳过。
            }

            return toolCalls.toString();

        } catch (Exception e) {
            System.err.println("处理ToolExecutionRequests时发生错误: " + e.getMessage());
            return toolExecutionRequestsJson; // 保证在出错时返回原始数据
        }
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


}
