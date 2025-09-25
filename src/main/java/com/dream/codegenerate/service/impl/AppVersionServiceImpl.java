package com.dream.codegenerate.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.dream.codegenerate.constant.AppConstant;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.model.dto.appVersion.AppVersionCompareRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionRestoreRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionSaveRequest;
import com.dream.codegenerate.model.entity.ChatHistory;
import com.dream.codegenerate.model.enums.AppVersionStoreTypeEnum;
import com.dream.codegenerate.model.enums.CodeGenTypeEnum;
import com.dream.codegenerate.model.vo.appVersion.AppVersionCompareVO;
import com.dream.codegenerate.model.vo.appVersion.AppVersionVO;
import com.dream.codegenerate.service.ChatHistoryService;
import com.dream.codegenerate.service.ScreenshotService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.dream.codegenerate.constant.UserConstant;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.exception.ThrowUtils;
import com.dream.codegenerate.model.dto.appVersion.AppVersionQueryRequest;
import com.dream.codegenerate.model.entity.App;
import com.dream.codegenerate.model.entity.AppVersion;
import com.dream.codegenerate.mapper.AppVersionMapper;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.vo.appVersion.AppVersionQueryVO;
import com.dream.codegenerate.service.AppService;
import com.dream.codegenerate.service.AppVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.dream.codegenerate.constant.AppConstant.EXCLUDED_FOLDERS;
import static com.dream.codegenerate.model.entity.table.AppVersionTableDef.APP_VERSION;
import static com.mybatisflex.core.query.QueryMethods.ifNull;

/**
 * 应用版本 服务层实现。
 *
 * dream
 */
@Service
@Slf4j
public class AppVersionServiceImpl extends ServiceImpl<AppVersionMapper, AppVersion>  implements AppVersionService{

    @Resource
    @Lazy
    private AppService appService;

    @Resource
    @Lazy
    private ChatHistoryService chatHistoryService;

    @Resource
    private ScreenshotService screenshotService;

    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();


    @Value("${server.port}")
    private String serverPort;

    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Transactional
    @Override
    public Long createNewVersion(AppVersionSaveRequest appVersionSaveRequest, User loginUser) {
        Long appId = appVersionSaveRequest.getAppId();
        App app = checkAppPermission(appId, loginUser);
        // 1. 获取当前最新版本号
        QueryWrapper queryWrapper = QueryWrapper.create()
                .select(APP_VERSION.VERSION, APP_VERSION.CHAT_HISTORY_ID)
                .from(APP_VERSION)
                .where(APP_VERSION.APP_ID.eq(appId))
                .orderBy(APP_VERSION.VERSION.desc())
                .limit(1);
        AppVersion latestVersion = this.getOne(queryWrapper);
        ChatHistory lastHistory = chatHistoryService.getLastHistory(appId);
        Long chatHistoryId = lastHistory == null ? null : lastHistory.getId();


        ThrowUtils.throwIf(lastHistory == null, ErrorCode.NOT_FOUND_ERROR, "未找到该应用的最新对话");
        if(ObjUtil.isNotEmpty(latestVersion)){
            ThrowUtils.throwIf(chatHistoryId<=latestVersion.getChatHistoryId(), ErrorCode.PARAMS_ERROR, "当前版本已保存，请勿多次保存");;
        }
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID错误");

        String projectDirName = appVersionSaveRequest.getCodeGenType().getValue()+"_" + appId;
        String projectFullPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + projectDirName;
        String message = appVersionSaveRequest.getMessage();
        CodeGenTypeEnum codeGenType = appVersionSaveRequest.getCodeGenType();

        // 2. 【核心】使用虚拟线程并发读取整个项目，并打包成 JSON
        String codeContent = packProjectToJson(projectFullPath);

        int nextVersion = 1;
        if (latestVersion!=null&&latestVersion.getVersion() != null) {
            nextVersion = latestVersion.getVersion() + 1;
        }
        // 2. 构建新版本对象并保存

        AppVersion newAppVersion = AppVersion.builder()
                .appId(appId)
                .version(nextVersion)
                .content(codeContent)
                .message(message)
                .storageType(AppVersionStoreTypeEnum.FULL) // 默认使用全量存储
                .chatHistoryId(chatHistoryId) // 关联历史对话ID
                .build();

        boolean result = this.save(newAppVersion);


        if (!result || newAppVersion.getId() == null) {
            log.error("自动创建应用版本失败, appId: {}", appId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建版本失败");
        }
        Long newVersionId = newAppVersion.getId();
        app.setCurrentVersion(newVersionId);
        appService.updateById(app);

        log.info("成功为应用 {} 创建新版本 V{} (ID: {})，即将开始生成封面...", appId, nextVersion, newVersionId);


        // 5. 【核心修改】: 异步生成截图并更新版本记录
        CompletableFuture.runAsync(() -> {
            try {
                // 【核心修改】: 在这里本地拼装出和前端完全一样的预览URL
                // 【核心修改】: 使用 localhost 和注入的配置，构建最直接的内部访问URL
                String staticBaseUrl = String.format("http://localhost:%s%s/static", serverPort, contextPath);
                String baseUrl = String.format("%s/%s/", staticBaseUrl, projectDirName);
                String previewUrl;
                if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
                    previewUrl = baseUrl + "dist/index.html";
                } else {
                    previewUrl = baseUrl;
                }
                // 调用截图服务
                String coverUrl = screenshotService.generateAndUploadScreenshot(previewUrl);

                // 更新数据库
                AppVersion updateCoverVo = new AppVersion();
                updateCoverVo.setId(newVersionId);
                updateCoverVo.setCover(coverUrl);
                this.updateById(updateCoverVo);
                log.info("版本 {} 的封面已成功生成并更新。", newVersionId);
            } catch (Exception e) {
                log.error("异步生成版本 {} 的封面失败", newVersionId, e);
            }
        });

        return newVersionId;
    }

    @Override
    public Page<AppVersionQueryVO> listByPage(AppVersionQueryRequest appVersionQueryRequest, User loginUser) {
        ThrowUtils.throwIf(appVersionQueryRequest.getAppId() == null || appVersionQueryRequest.getAppId() <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(appVersionQueryRequest.getPageSize() <= 0 || appVersionQueryRequest.getPageSize() > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appVersionQueryRequest.getAppId());
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        QueryWrapper queryWrapper = this.getQueryWrapper(appVersionQueryRequest);
        Page<AppVersion> page = this.page(Page.of(1, appVersionQueryRequest.getPageSize()), queryWrapper);
        // 数据脱敏
        Page<AppVersionQueryVO> AppVersionQueryVOPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        List<AppVersionQueryVO> appVersionQueryVOList = this.getAppVersionQueryVOList(page.getRecords());
        AppVersionQueryVOPage.setRecords(appVersionQueryVOList);
        return AppVersionQueryVOPage;
    }


    /**
     * 获取查询包装类
     *
     * @param appVersionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(AppVersionQueryRequest appVersionQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (appVersionQueryRequest == null) {
            return queryWrapper;
        }
        Long appId = appVersionQueryRequest.getAppId();
        String storageType = appVersionQueryRequest.getStorageType();
        Long chatHistoryId = appVersionQueryRequest.getChatHistoryId();
        LocalDateTime lastCreateTime = appVersionQueryRequest.getLastCreateTime();
        queryWrapper.eq("appId", appId);
        if(ObjUtil.isNotEmpty(chatHistoryId)){
            queryWrapper.eq("chatHistoryId", chatHistoryId);
        }
        if(ObjUtil.isNotEmpty(storageType)){
            queryWrapper.eq("storageType", storageType);
        }
        if(ObjUtil.isNotEmpty(lastCreateTime)){
            queryWrapper.lt("createTime", lastCreateTime);
        }
        return queryWrapper;
    }


    @Override
    public List<AppVersionQueryVO> getAppVersionQueryVOList(List<AppVersion> appVersionList) {
        if (CollUtil.isEmpty(appVersionList)) {
            return new ArrayList<>();
        }
        return appVersionList.stream()
                .map(AppVersion::toAppVersionQueryVO)
                .collect(Collectors.toList());
    }

    @Override
    public Boolean restore(AppVersionRestoreRequest request, User loginUser) {
        Long appId = request.getAppId();
        App app =checkAppPermission(appId, loginUser);
        Long id = request.getId();

        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID错误");
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "指定的应用版本不存在");

        // 2. 获取应用信息，得到项目根路径
        AppVersion appVersion = this.getById(id);
        ThrowUtils.throwIf(Objects.equals(appVersion.getId(), app.getCurrentVersion()), ErrorCode.PARAMS_ERROR, "不能回滚当前版本");
        // 假设 AppConstant.CODE_OUTPUT_ROOT_DIR = "D:/codegenerate/yu-ai-code-mother/tmp";
        String subDirName = app.getCodeGenType() + "_" + appId;

// 使用 Paths.get() 来安全地构建路径
        Path projectRootPathObj = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, subDirName);
        String projectRootPath = projectRootPathObj.toString();

        // 3. 查询要恢复的版本信息
        String codeContentJson = appVersion.getContent();
        ThrowUtils.throwIf(codeContentJson == null || codeContentJson.isEmpty(), ErrorCode.SYSTEM_ERROR, "版本内容为空，无法恢复");
        log.info("开始恢复应用 {} 到版本 {}", appId, id);

        Path projectRoot = Paths.get(projectRootPath).toAbsolutePath();
        Path stagingPath;
        String stagingDirName = "staging-" + System.currentTimeMillis();

        // 1. 在项目根目录 *内部* 创建一个唯一的临时“暂存”目录

        try {
            stagingPath = projectRoot.resolve(stagingDirName);
            Files.createDirectories(stagingPath);
            log.info("已在项目内部创建临时暂存目录: {}", stagingPath);

        } catch (IOException e) {
            log.error("无法创建临时暂存目录", e);
            return false;
        }

        try {
            // 为本次清理操作创建一个包含暂存目录的动态排除列表
            Set<String> currentExclusions = new HashSet<>(EXCLUDED_FOLDERS);
            currentExclusions.add(stagingDirName);
            // 2. 【异步任务A】将新版本的内容写入临时暂存目录
            CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
                try {
                    log.info("开始异步写入版本 {} 的文件到暂存目录", id);
                    writeJsonToProject(codeContentJson, stagingPath.toString());
                    log.info("暂存目录写入完成");
                } catch (IOException e) {
                    throw new CompletionException("写入暂存目录失败", e);
                }
            }, executorService);

            // 3. 【异步任务B】清空当前线上的项目目录
            CompletableFuture<Void> clearFuture = CompletableFuture.runAsync(() -> {
                try {
                    log.info("开始异步清空项目目录 (排除 {})", currentExclusions);
                    clearProjectDirectory(projectRoot.toString(), currentExclusions);
                    log.info("项目目录清空完成");
                } catch (IOException e) {
                    throw new CompletionException("清空项目目录失败", e);
                }
            }, executorService);

            // 4. 等待两个异步任务都完成
            log.info("等待写入和清空操作完成...");
            CompletableFuture.allOf(writeFuture, clearFuture).join();
            log.info("写入和清空操作均已完成");

            // 5. 【核心切换】将暂存目录的所有内容移动到项目目录
            log.info("正在将文件从暂存目录移动到项目目录...");
            moveContents(stagingPath, projectRoot);
            log.info("文件移动完成，版本切换成功");

        } catch (CompletionException e) {
            log.error("版本恢复期间，异步操作发生异常", e.getCause());
             throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本恢复失败：" + e.getCause().getMessage());

        } catch (IOException e) {
            log.error("版本恢复期间，移动文件时发生IO异常", e);
             throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本恢复失败，文件操作错误");

        } catch (Exception e) {
            log.error("版本恢复期间发生未知异常", e);
             throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本恢复失败，发生未知错误");

        } finally {
            // 6. 【重要】无论成功与否，都必须尝试清理临时暂存目录
            try {
                log.info("正在清理暂存目录: {}", stagingPath);
                if (Files.exists(stagingPath)) {
                    try (Stream<Path> walk = Files.walk(stagingPath)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(path -> {
                                    try {
                                        Files.delete(path);
                                    } catch (IOException ex) {
                                        log.error("清理暂存文件失败: {}", path, ex);
                                    }
                                });
                    }
                }
                log.info("暂存目录清理完成");
            } catch (IOException e) {
                log.error("无法清理暂存目录: {}", stagingPath, e);
            }
        }
        app.setCurrentVersion( id);
        appService.updateById(app);
        log.info("成功恢复应用 {} 到版本 {}", appId, id);
        return true;

    }
    @Override
    public AppVersionVO getAppVersionVOById(long id, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 1. 获取版本数据
        AppVersion appVersion = this.getById(id);
        ThrowUtils.throwIf(appVersion == null, ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        // 2. 权限校验
        checkAppPermission(appVersion.getAppId(), loginUser);
        // 3. 封装并返回
        return AppVersion.toAppVersionVO(appVersion);
    }
    @Override
    public AppVersionCompareVO compare(AppVersionCompareRequest appVersionCompareRequest, User loginUser) {
        Long appId = appVersionCompareRequest.getAppId();
        Integer fromVersionId = appVersionCompareRequest.getFromVersion();
        Integer toVersionId = appVersionCompareRequest.getToVersion();

        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID错误");
        ThrowUtils.throwIf(fromVersionId == null || fromVersionId <= 0, ErrorCode.PARAMS_ERROR, "起始版本号错误");
        ThrowUtils.throwIf(toVersionId == null || toVersionId <= 0, ErrorCode.PARAMS_ERROR, "目标版本号错误");
        ThrowUtils.throwIf(fromVersionId.equals(toVersionId), ErrorCode.PARAMS_ERROR, "不能对比相同版本");

        // 2. 权限校验
        checkAppPermission(appId, loginUser);

        // 3. 查询两个版本的完整信息
        AppVersion fromVersion = getOne(
                QueryWrapper.create().where(APP_VERSION.APP_ID.eq(appId)).and(APP_VERSION.ID.eq(fromVersionId))
        );
        ThrowUtils.throwIf(fromVersion == null, ErrorCode.NOT_FOUND_ERROR, "起始版本不存在");

        AppVersion toVersion = getOne(
                QueryWrapper.create().where(APP_VERSION.APP_ID.eq(appId)).and(APP_VERSION.ID.eq(toVersionId))
        );
        ThrowUtils.throwIf(toVersion == null, ErrorCode.NOT_FOUND_ERROR, "目标版本不存在");

        // 4. 封装返回
        AppVersionVO fromVersionVO = AppVersion.toAppVersionVO(fromVersion);
        AppVersionVO toVersionVO = AppVersion.toAppVersionVO(toVersion);

        return AppVersionCompareVO.builder()
                .fromVersionData(fromVersionVO)
                .toVersionData(toVersionVO)
                .build();
    }

    @Override
    public boolean deleteByAppId(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(String.valueOf(APP_VERSION.ID), id);
        return this.remove(queryWrapper);
    }
    /**
     * 内部方法：校验用户对应用的权限
     */
    private App checkAppPermission(Long appId, User loginUser) {
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权操作该应用");
        return app;
    }

    /**
     * 将整个项目目录打包成一个 JSON 字符串。
     * 使用虚拟线程并发读取文件以提升 I/O 性能。
     *
     * @param projectRootPath 项目的绝对路径
     * @return 代表整个项目文件结构的 JSON 字符串
     */
    private String packProjectToJson(String projectRootPath) {
        Path root = Paths.get(projectRootPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目目录不存在: " + projectRootPath);
        }

        // 使用线程安全的 Map 来收集结果
        ConcurrentMap<String, String> fileContents = new ConcurrentHashMap<>();
        log.info("开始使用虚拟线程打包项目: {}", projectRootPath);

        // 使用 Java 21+ 的虚拟线程执行器
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            // 使用 Files.walk 遍历所有文件
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> {
                            // 检查文件路径是否在排除目录中
                            Path relativePath = root.relativize(path);
                            for (int i = 0; i < relativePath.getNameCount(); i++) {
                                if (EXCLUDED_FOLDERS.contains(relativePath.getName(i).toString())) {
                                    return false; // 排除该文件
                                }
                            }
                            return true; // 不排除该文件
                        })
                        .forEach(path -> {
                            // 为每个文件提交一个读取任务
                            Future<?> future = executor.submit(() -> {
                                try {
                                    // 计算相对路径，并统一使用 '/' 作为分隔符
                                    String relativePath = root.relativize(path).toString().replace('\\', '/');
                                    String content = Files.readString(path, StandardCharsets.UTF_8);
                                    fileContents.put(relativePath, content);
                                } catch (Exception e) {
                                    ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "保存版本时发生错误");
                                    // 在并发任务中，可以选择记录错误并继续，或抛出异常
                                }
                            });
                            futures.add(future);
                        });
            }

            // 等待所有读取任务完成
            for (Future<?> future : futures) {
                // 调用 get() 会阻塞直到任务完成，如果任务抛出异常，这里也会抛出
                future.get();
            }

        } catch (Exception e) {
            // 中断异常需要恢复中断状态
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("打包项目文件时发生严重错误", e);
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "打包项目文件时发生严重错误");
        }

        log.info("项目打包完成，共 {} 个文件", fileContents.size());
        // 将 Map 序列化为 JSON 字符串
        return JSONUtil.toJsonStr(fileContents);
    }



    /**
     * 将源目录的所有内容移动到目标目录。
     */
    private void moveContents(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.filter(path -> !path.equals(source))
                    .forEach(sourcePath -> {
                        Path targetPath = target.resolve(source.relativize(sourcePath));
                        try {
                            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                            log.debug("已移动: {} -> {}", sourcePath, targetPath);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * 清理项目目录下的所有内容。
     */
    private void clearProjectDirectory(String projectRootPath, Set<String> exclusions) throws IOException {
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

            // 【增加验证步骤】检查文件/目录是否真的被删除了
            if (Files.exists(file.toPath())) {
                // 如果还存在，就主动抛出异常，让失败暴露出来
                throw new IOException("验证删除失败，文件或目录依然存在: " + file.getAbsolutePath());
            }
            log.debug("已确认删除: {}", file.getAbsolutePath());
        }
    }
    /**
     * 将包含文件内容的JSON字符串解析并写入到项目目录中。
     */
    private void writeJsonToProject(String jsonContent, String projectRootPath) throws IOException {
        // 这里的 Map.of 是一个示例，请替换为您项目中实际使用的JSON解析库
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
