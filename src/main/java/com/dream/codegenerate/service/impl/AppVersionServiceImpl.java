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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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



    @Value("${server.port}")
    private String serverPort;

    @Value("${server.servlet.context-path}")
    private String contextPath;
    @Transactional
    @Override
    public Long createNewVersion(AppVersionSaveRequest appVersionSaveRequest, User loginUser) {
        Long appId = appVersionSaveRequest.getAppId();
        checkAppPermission(appId, loginUser);
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
        String projectRootPath = app.getCodeGenType() +"_"+ appId;

        // 3. 查询要恢复的版本信息
        String codeContentJson = appVersion.getContent();
        ThrowUtils.throwIf(codeContentJson == null || codeContentJson.isEmpty(), ErrorCode.SYSTEM_ERROR, "版本内容为空，无法恢复");

        log.info("开始恢复应用 {} 到版本 {}", appId, id);

        try {
            // 4. 【核心】清空项目目录（会智能跳过排除的文件夹）
            log.info("正在清空项目目录: {}", projectRootPath);
            clearProjectDirectory(projectRootPath);
            log.info("项目目录清空完成");

            // 5. 【核心】将版本内容解压并写回文件系统
            log.info("正在写入版本 {} 的文件内容", id);
            writeJsonToProject(codeContentJson, projectRootPath);
            log.info("文件内容写入完成");

        } catch (IOException e) {
            log.error("版本恢复期间发生IO异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本恢复失败，文件操作错误");
        } catch (Exception e) {
            log.error("版本恢复期间发生未知异常", e);
            // 可以根据需要决定是否重新抛出为自定义异常
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本恢复失败，发生未知错误");
        }

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
        Integer fromVersionNum = appVersionCompareRequest.getFromVersion();
        Integer toVersionNum = appVersionCompareRequest.getToVersion();

        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID错误");
        ThrowUtils.throwIf(fromVersionNum == null || fromVersionNum <= 0, ErrorCode.PARAMS_ERROR, "起始版本号错误");
        ThrowUtils.throwIf(toVersionNum == null || toVersionNum <= 0, ErrorCode.PARAMS_ERROR, "目标版本号错误");
        ThrowUtils.throwIf(fromVersionNum.equals(toVersionNum), ErrorCode.PARAMS_ERROR, "不能对比相同版本");

        // 2. 权限校验
        checkAppPermission(appId, loginUser);

        // 3. 查询两个版本的完整信息
        AppVersion fromVersion = getOne(
                QueryWrapper.create().where(APP_VERSION.APP_ID.eq(appId)).and(APP_VERSION.VERSION.eq(fromVersionNum))
        );
        ThrowUtils.throwIf(fromVersion == null, ErrorCode.NOT_FOUND_ERROR, "起始版本不存在");

        AppVersion toVersion = getOne(
                QueryWrapper.create().where(APP_VERSION.APP_ID.eq(appId)).and(APP_VERSION.VERSION.eq(toVersionNum))
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
                                if (AppConstant.EXCLUDED_FOLDERS.contains(relativePath.getName(i).toString())) {
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
     * 清理项目目录下的所有内容，但会跳过 AppConstant.EXCLUDED_FOLDERS 中定义的排除目录。
     *
     * @param projectRootPath 项目的绝对路径
     * @throws IOException 如果发生I/O错误
     */
    private void clearProjectDirectory(String projectRootPath) throws IOException {
        Path root = Paths.get(projectRootPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            log.warn("项目目录不存在或不是一个目录，无需清理: {}", projectRootPath);
            // 目录不存在，可以直接返回，后续写入时会自动创建
            return;
        }

        // 使用Files.walk遍历所有文件和目录
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()) // 逆序删除，保证先删除文件再删除空目录
                    .filter(path -> !path.equals(root)) // 避免删除根目录本身
                    .filter(path -> {
                        // 检查路径的任何部分是否匹配排除列表
                        Path relativePath = root.relativize(path);
                        for (Path part : relativePath) {
                            if (AppConstant.EXCLUDED_FOLDERS.contains(part.toString())) {
                                log.debug("跳过受保护的路径: {}", path);
                                return false; // 如果是受保护的路径，则过滤掉，不进行删除
                            }
                        }
                        return true; // 否则，保留以进行删除
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.debug("已删除: {}", path);
                        } catch (IOException e) {
                            // 此处可以记录一个更详细的错误，但在逆序删除中，
                            // 目录非空等问题通常不会发生。
                            log.error("删除文件/夹失败: {}", path, e);
                        }
                    });
        }
    }

    /**
     * 将包含文件内容的JSON字符串解析并写入到项目目录中
     *
     * @param jsonContent     文件内容的JSON字符串
     * @param projectRootPath 项目根目录
     * @throws IOException 如果发生I/O错误
     */
    private void writeJsonToProject(String jsonContent, String projectRootPath) throws IOException {
        // 使用 Hutool JSON 工具反序列化为 Map<String, String>
        // Key: 相对路径, Value: 文件内容
        Map<String, String> fileContents = JSONUtil.toBean(jsonContent, new TypeReference<Map<String, String>>() {
        }, false);

        if (fileContents == null || fileContents.isEmpty()) {
            log.warn("版本内容数据为空，没有文件需要写入。");
            return;
        }

        // 遍历Map，逐个创建文件并写入内容
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String relativePathStr = entry.getKey();
            String content = entry.getValue();

            Path filePath = Paths.get(projectRootPath, relativePathStr);

            // 确保父级目录存在，如果不存在则自动创建
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.debug("已创建目录: {}", parentDir);
            }

            // 将内容写入文件，如果文件已存在则覆盖
            Files.writeString(filePath, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("已写入文件: {}", filePath);
        }
    }
}
