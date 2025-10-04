package com.dream.codegenerate.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 构建 Vue 项目
 */
@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建 Vue 项目
     *
     * @param projectPath
     * @return Future对象，可以通过它获取构建结果
     */
    public CompletableFuture<BuildResult> buildProjectAsync(String projectPath) {
        return CompletableFuture.supplyAsync(() -> buildProject(projectPath));
    }

    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 构建结果
     */
    public BuildResult buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            String errorMsg = "项目目录不存在：" + projectPath;
            log.error(errorMsg);
            return new BuildResult(false, errorMsg);
        }

        // 检查是否有 package.json 文件
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            String errorMsg = "项目目录中没有 package.json 文件：" + projectPath;
            log.error(errorMsg);
            return new BuildResult(false, errorMsg);
        }

        log.info("开始构建 Vue 项目：{}", projectPath);

        // 删除 node_modules 目录
        try {
            File nodeModulesDir = new File(projectDir, "node_modules");
            if (nodeModulesDir.exists() && nodeModulesDir.isDirectory()) {
                log.info("发现已存在的 node_modules 目录，开始删除：{}", nodeModulesDir.getAbsolutePath());
                FileUtils.deleteDirectory(nodeModulesDir);
                log.info("node_modules 目录删除成功。");
            }
        } catch (IOException e) {
            String errorMsg = "删除 node_modules 目录失败：" + projectPath;
            log.error(errorMsg, e);
            return new BuildResult(false, errorMsg, e.getMessage());
        }

        // 执行 npm install
        BuildResult installResult = executeNpmInstall(projectDir);
        if (!installResult.isSuccess()) {
            log.error("npm install 执行失败：{}", projectPath);
            return installResult;
        }

        // 执行 npm run build
        BuildResult buildResult = executeNpmBuild(projectDir);
        if (!buildResult.isSuccess()) {
            log.error("npm run build 执行失败：{}", projectPath);
            return buildResult;
        }

        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            String errorMsg = "构建完成但 dist 目录未生成：" + projectPath;
            log.error(errorMsg);
            return new BuildResult(false, errorMsg);
        }

        log.info("Vue 项目构建成功，dist 目录：{}", projectPath);
        return new BuildResult(true, "Vue 项目构建成功");
    }

    /**
     * 执行 npm install 命令
     */
    private BuildResult executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300);
    }

    /**
     * 执行 npm run build 命令
     */
    private BuildResult executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 300);
    }

    /**
     * 根据操作系统构造命令
     */
    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    /**
     * 操作系统检测
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行命令并捕获输出
     */
    private BuildResult executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);

            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    command.split("\\s+")
            );

            // 捕获标准输出和错误输出
            String output = captureStream(process.getInputStream());
            String errorOutput = captureStream(process.getErrorStream());

            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                String errorMsg = "命令执行超时（" + timeoutSeconds + "秒）";
                log.error(errorMsg);
                process.destroyForcibly();
                return new BuildResult(false, errorMsg, errorOutput);
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return new BuildResult(true, "命令执行成功", output);
            } else {
                String errorMsg = "命令执行失败，退出码: " + exitCode;
                log.error(errorMsg);
                return new BuildResult(false, errorMsg, errorOutput);
            }
        } catch (Exception e) {
            String errorMsg = "执行命令失败: " + command;
            log.error(errorMsg + ", 错误信息: {}", e.getMessage());
            return new BuildResult(false, errorMsg, e.getMessage());
        }
    }

    /**
     * 捕获流内容
     */
    private String captureStream(InputStream inputStream) {
        try {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("捕获流内容失败: {}", e.getMessage());
            return "无法读取输出: " + e.getMessage();
        }
    }
}
