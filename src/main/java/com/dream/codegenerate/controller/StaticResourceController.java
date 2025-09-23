package com.dream.codegenerate.controller;

import com.dream.codegenerate.common.BaseResponse;
import com.dream.codegenerate.common.ResultUtils;
import com.dream.codegenerate.constant.AppConstant;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.exception.ThrowUtils;
import com.dream.codegenerate.model.dto.staticFile.StaticFilesListRequest;
import com.dream.codegenerate.model.enums.CodeGenTypeEnum;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 静态资源访问
 */
@RestController
@RequestMapping("/static")
@Slf4j
public class StaticResourceController {

    // 应用生成根目录（用于浏览）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;


    /**
     * 提供静态资源访问，支持目录重定向
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 构建文件路径
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            // 检查文件是否存在
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 新增：列出指定静态资源目录下的所有文件
     * 这个方法会响应前端的 /api/static/list?path=... 请求
     * @return BaseResponse 包装的文件相对路径列表
     */
    @PostMapping("/list")
    public BaseResponse<List<String>> listStaticFiles(@RequestBody StaticFilesListRequest  filesListRequest,HttpServletRequest request) {
        CodeGenTypeEnum codeGenType = filesListRequest.getCodeGenType();
        Long appId = filesListRequest.getAppId();

        // 安全性检查，确保参数不为空
        if (codeGenType == null || appId == null || appId <= 0) {
            return new BaseResponse<>(400, null, "无效的参数");
        }

        // 组合成目录路径，使用枚举的名称来获取字符串表示
        Path dirPath = Paths.get(PREVIEW_ROOT_DIR, codeGenType.getValue() + "_" + appId);
        File dir = dirPath.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            return new BaseResponse<>(0, Collections.emptyList(), "目录不存在或尚未生成");
        }

        if (!dir.exists() || !dir.isDirectory()) {
            return new BaseResponse<>(0, Collections.emptyList(), "目录不存在或尚未生成");
        }

        try (Stream<Path> stream = Files.walk(dirPath)) {
            List<String> fileList = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        Path relativePath = dirPath.relativize(path);
                        return relativePath.getNameCount() > 0 &&
                                !AppConstant.EXCLUDED_FOLDERS.contains(relativePath.getName(0).toString());
                    })
                    .map(dirPath::relativize)
                    .map(p -> p.toString().replace(File.separatorChar, '/'))
                    .collect(Collectors.toList());

            return ResultUtils.success(fileList);
        } catch (IOException e) {
             log.error("Error listing files for path: {}", codeGenType.getValue() + "_" + appId, e);
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "服务器内部错误，无法读取文件列表");
        }
        return ResultUtils.success(Collections.emptyList());
    }





    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
