package com.dream.codegenerate.service.impl;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AppVersionServiceImplTest {
    // 测试将在上述目录下的一个安全子目录中进行
    private Path testDirPath;
    // 模拟你的常量类，直接指向目标根目录
    private static final String CODE_OUTPUT_ROOT_DIR = "D:\\codegenerate\\yu-ai-code-mother\\tmp\\code_output\\multi_file_328424654517604352";

    @BeforeEach
    void setup() throws IOException {
        Path rootPath = Paths.get(CODE_OUTPUT_ROOT_DIR);
        testDirPath = rootPath.resolve("direct_delete_test");

        System.out.println("测试将在以下确切目录进行: " + testDirPath.toAbsolutePath());

        // 准备测试环境
        System.out.println("\n--- 步骤 1: 准备测试环境 ---");
        setupTestDirectory(testDirPath);
    }

    @AfterEach
    void tearDown() throws IOException {
        System.out.println("\n--- 测试后清理 ---");
        if (Files.exists(testDirPath)) {
            System.out.println("正在清理测试目录: " + testDirPath.toAbsolutePath());
            try {
                FileUtils.deleteDirectory(testDirPath.toFile());
            } catch (IOException e) {
                System.err.println("警告: 清理测试目录时发生异常: " + e.getMessage());
            } finally {
                if (Files.exists(testDirPath)) {
                    System.err.println("警告: 清理测试目录失败。");
                } else {
                    System.out.println("清理成功。");
                }
            }
        }
    }

    @Test
    void testDeletionInTargetDirectory() {
        try {
            // 执行删除操作
            System.out.println("\n--- 步骤 2: 执行删除操作 ---");
            deleteDirectoryAndVerify(testDirPath.toFile());

            // 最终检查
            System.out.println("\n--- 步骤 3: 最终检查 ---");
            if (Files.exists(testDirPath)) {
                System.err.println("测试失败: 最终检查发现目录依然存在！");
            } else {
                System.out.println("测试成功: 最终检查确认目录已被删除。");
            }

        } catch (IOException e) {
            System.err.println("\n测试过程中发生异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupTestDirectory(Path rootPath) throws IOException {
        if (Files.exists(rootPath)) {
            FileUtils.deleteDirectory(rootPath.toFile());
        }
        Files.createDirectories(rootPath);

        Path file1 = rootPath.resolve("file1.txt");
        Files.writeString(file1, "hello", StandardOpenOption.CREATE);

        Path subDir = rootPath.resolve("sub_dir");
        Files.createDirectories(subDir);

        Path file2 = subDir.resolve("file2.txt");
        Files.writeString(file2, "world", StandardOpenOption.CREATE);

        System.out.println("环境准备成功。");
    }

    private void deleteDirectoryAndVerify(File dir) throws IOException {
        System.out.println("开始删除目录: " + dir.getAbsolutePath());
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("目录不存在，无需删除。");
            return;
        }

        FileUtils.deleteDirectory(dir);

        if (Files.exists(dir.toPath())) {
            throw new IOException("验证删除失败，目录依然存在: " + dir.getAbsolutePath());
        }

        System.out.println("已确认删除: " + dir.getAbsolutePath());
    }

}
