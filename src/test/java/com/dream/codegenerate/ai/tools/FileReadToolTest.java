package com.dream.codegenerate.ai.tools;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileReadTool 测试类
 */
class FileReadToolTest {

    private FileReadTool fileReadTool = new FileReadTool();

    @Test
    void testGetToolName() {
        assertEquals("readFile", fileReadTool.getToolName());
    }

    @Test
    void testGetDisplayName() {
        assertEquals("读取文件", fileReadTool.getDisplayName());
    }

    @Test
    void testGenerateToolExecutedResult() {
        JSONObject arguments = new JSONObject();
        arguments.put("relativeFilePath", "test.txt");

        String result = fileReadTool.generateToolExecutedResult(arguments);
        assertTrue(result.contains("读取文件"));
        assertTrue(result.contains("test.txt"));
    }
}