package com.dream.codegenerate.utils;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.dream.codegenerate.constant.AppConstant;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class ProjectPackagerUtil {

    // 将虚拟线程执行器定义为成员变量，以供重用
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();


    /**
     * 高效地遍历项目文件，排除指定目录和文件，并将内容打包成 JSON 字符串。
     *
     * @param projectRootPath 项目根目录的路径
     * @return 包含文件相对路径和文件内容的 JSON 字符串
     */
    public String packProjectToJson(String projectRootPath) {
        Path root = Paths.get(projectRootPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目目录不存在: " + projectRootPath);
        }

        ConcurrentMap<String, String> fileContents = new ConcurrentHashMap<>();
        log.info("开始使用虚拟线程打包项目: {}", projectRootPath);
        log.debug("生效的排除文件夹列表: {}", AppConstant.EXCLUDED_FOLDERS);

        try {
            List<Future<?>> futures = new ArrayList<>();

            // 使用 walkFileTree 和 SimpleFileVisitor 实现真正的目录跳过，这是最高效的方式
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // 如果目录名在排除列表中 (并且不是根目录本身)，则跳过整个子树
                    if (!dir.equals(root) && dir.getFileName() != null && AppConstant.EXCLUDED_FOLDERS.contains(dir.getFileName().toString())) {
                        log.debug("正在跳过整个目录 (SKIP_SUBTREE): {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 检查文件名是否在排除列表中
                    if (file.getFileName() != null && AppConstant.EXCLUDED_FILES.contains(file.getFileName().toString())) {
                        log.debug("跳过排除的文件: {}", file);
                        return FileVisitResult.CONTINUE;
                    }

                    // 为每个符合条件的文件提交一个读取任务
                    Future<?> future = executor.submit(() -> {
                        try {
                            String relativePath = root.relativize(file).toString().replace('\\', '/');
                            String content = Files.readString(file, StandardCharsets.UTF_8);
                            fileContents.put(relativePath, content);
                        } catch (Exception e) {
                            log.error("读取文件 {} 时出错，请检查该文件是否为文本文件或应加入排除列表", file, e);
                            throw new CompletionException("读取文件时出错: " + file, e);
                        }
                    });
                    futures.add(future);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    log.error("访问文件失败: {}", file, exc);
                    return FileVisitResult.CONTINUE; // 即使单个文件访问失败，也继续遍历其他文件
                }
            });

            // 等待所有异步任务完成
            for (Future<?> future : futures) {
                future.get();
            }

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("打包项目文件时发生严重错误", e);
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "打包项目文件时发生严重错误: " + e.getMessage());
        }

        log.info("项目打包完成，共 {} 个文件", fileContents.size());
        return JSONUtil.toJsonStr(fileContents);
    }
    /**
     * 将源目录的所有内容移动到目标目录。
     */
    public void moveContents(Path source, Path target) throws IOException {
        // 使用 Files.list 获取源目录下的顶级文件和目录流
        try (Stream<Path> stream = Files.list(source)) {
            // 对每个顶级路径执行移动操作
            for (Path sourcePath : stream.toList()) {
                // 构建目标路径，即 target 目录 + sourcePath 的文件名
                Path targetPath = target.resolve(sourcePath.getFileName());
                // 移动文件或目录。当移动目录时，其下的所有内容会一并被移动。
                Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.debug("已移动: {} -> {}", sourcePath, targetPath);
            }
        }
    }

    /**
     * 清理项目目录下的所有内容。
     */
    public void clearProjectDirectory(String projectRootPath, Set<String> exclusions) throws IOException {
        File rootDir = new File(projectRootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            log.warn("项目目录不存在或不是一个目录，无需清理: {}", projectRootPath);
            return;
        }

        File[] files = rootDir.listFiles();
        if (files == null) {
            log.warn("无法列出目录内容，可能存在权限问题: {}", projectRootPath);
            return;
        }

        for (File file : files) {
            if (exclusions.contains(file.getName())) {
                log.debug("跳过受保护的路径: {}", file.getAbsolutePath());
                continue;
            }

            log.debug("正在删除: {}", file.getAbsolutePath());
            if (file.isDirectory()) {
                FileUtils.deleteDirectory(file);
            } else {
                Files.delete(file.toPath());
            }

            if (Files.exists(file.toPath())) {
                throw new IOException("验证删除失败，文件或目录依然存在: " + file.getAbsolutePath());
            }
            log.debug("已确认删除: {}", file.getAbsolutePath());
        }
    }
    /**
     * 将包含文件内容的JSON字符串解析并写入到项目目录中。
     */
    public void writeJsonToProject(String jsonContent, String projectRootPath) throws IOException {
        Map<String, String> fileContents = JSONUtil.toBean(jsonContent, new TypeReference<Map<String, String>>() {}, false);

        if (fileContents == null || fileContents.isEmpty()) {
            log.warn("版本内容数据为空，没有文件需要写入。");
            return;
        }

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            Path filePath = Paths.get(projectRootPath, entry.getKey());
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            Files.writeString(filePath, entry.getValue(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

}
