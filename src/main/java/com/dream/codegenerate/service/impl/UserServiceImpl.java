package com.dream.codegenerate.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.dream.codegenerate.manager.CosManager;
import com.dream.codegenerate.model.dto.user.VipCode;
import com.dream.codegenerate.model.entity.AccessKey;
import com.dream.codegenerate.newapi.NewApiClient;
import com.dream.codegenerate.newapi.common.ApiResponse;
import com.dream.codegenerate.newapi.model.tokrn.request.CreateTokenRequest;
import com.dream.codegenerate.newapi.model.tokrn.response.TokenInfo;
import com.dream.codegenerate.service.AccessKeyService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.model.dto.user.UserQueryRequest;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.mapper.UserMapper;
import com.dream.codegenerate.model.enums.UserRoleEnum;
import com.dream.codegenerate.model.vo.LoginUserVO;
import com.dream.codegenerate.model.vo.UserVO;
import com.dream.codegenerate.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static cn.hutool.core.thread.ThreadUtil.sleep;
import static com.dream.codegenerate.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 *
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {


    @Resource
    private CosManager cosManager;

    @Resource
    private NewApiClient newApiClient;

    @Resource
    private AccessKeyService accessKeyService;
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 查询用户是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密密码
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 创建用户，插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserAvatar("https://dreamtof-1306162362.cos.ap-guangzhou.myqcloud.com/public/1924669923406053377/2025-09-23_tiDkZUSjkH6LRF88.webp");
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败，数据库错误");
        }
        //异步调用获取key
        CompletableFuture.runAsync(() -> getNewApiKey(user.getId()));
        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 4. 如果用户存在，记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 5. 返回脱敏的用户信息
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询当前用户信息
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id) // where id = ${id}
                .eq("userRole", userRole) // and userRole = ${userRole}
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "dream";
        return DigestUtils.md5DigestAsHex((userPassword + SALT).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Boolean updateMyAvatar(MultipartFile multipartFile, User user)  {
        // 1. 文件基础校验
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 2. 校验文件大小（1MB）
        long fileSize = multipartFile.getSize();
        final long ONE_MB = 1024 * 1024L;
        if (fileSize > ONE_MB) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过1MB");
        }

        // 3. 校验文件类型（例如：只允许 jpg, png, jpeg, gif）
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FilenameUtils.getExtension(originalFilename).toLowerCase();
        final List<String> allowedSuffixes = Arrays.asList("jpg", "jpeg", "png", "gif");
        if (!allowedSuffixes.contains(suffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文件格式，请上传 jpg, jpeg, png, gif 格式的图片");
        }

        // 4. 生成在 COS 中的唯一 Key，推荐带有业务路径和用户标识
        // 格式：avatar/{userId}/{uuid}.{suffix}
        String key = String.format("/avatar/%d/%s.%s",
                user.getId(),
                UUID.randomUUID().toString().substring(0, 8),
                suffix);

        File tempFile = null;
        try {
            // 5. 创建临时文件，将 MultipartFile 转换为 File
            // 这是为了调用需要 File 对象的 cosManager.uploadFile 方法
            tempFile = File.createTempFile("avatar_temp_", "." + suffix);
            multipartFile.transferTo(tempFile);

            // 6. 将文件上传到 COS
            String url = cosManager.uploadFile(key, tempFile);
            if (url == null) {
                // uploadFile 方法内部已经记录了 error log，这里直接抛出业务异常
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像上传失败");
            }
            // 7. 将新的头像 URL 更新到数据库
            User updateUser = new User();
            updateUser.setId(user.getId());
            updateUser.setUserAvatar(url);
            // 假设你使用的是 MyBatis-Plus
            boolean updateResult = this.updateById(updateUser);

            if (!updateResult) {
                // 如果数据库更新失败，这里可以考虑是否需要删除刚刚上传到COS的图片（补偿操作），根据业务复杂度决定
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据库更新头像地址失败");
            }

            // 8. 返回成功响应
            return true;

        } catch (IOException e) {
            log.error("处理上传文件时发生IO异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件处理失败");
        } finally {
            // 9. 清理临时文件，无论成功与否都应执行
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    // region ------- 以下代码为用户兑换会员功能 --------

    // 新增依赖注入
    @Autowired
    private ResourceLoader resourceLoader;

    // 文件读写锁（确保并发安全）
    private final ReentrantLock fileLock = new ReentrantLock();

    // VIP 角色常量（根据你的需求自定义）
    private static final String VIP_ROLE = "vip";

    /**
     * 兑换会员
     *
     * @param user
     * @param vipCode
     * @return
     */
    @Override
    public boolean exchangeVip(User user, String vipCode) {
        // 1. 参数校验
        if (user == null || StrUtil.isBlank(vipCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 读取并校验兑换码
        VipCode targetCode = validateAndMarkVipCode(vipCode);
        // 3. 更新用户信息
        updateUserVipInfo(user, targetCode.getCode());
        return true;
    }

    /**
     * 校验兑换码并标记为已使用
     */
    private VipCode validateAndMarkVipCode(String vipCode) {
        fileLock.lock(); // 加锁保证文件操作原子性
        try {
            // 读取 JSON 文件
            JSONArray jsonArray = readVipCodeFile();

            // 查找匹配的未使用兑换码
            List<VipCode> codes = JSONUtil.toList(jsonArray, VipCode.class);
            VipCode target = codes.stream()
                    .filter(code -> code.getCode().equals(vipCode) && !code.isHasUsed())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "无效的兑换码"));

            // 标记为已使用
            target.setHasUsed(true);

            // 写回文件
            writeVipCodeFile(JSONUtil.parseArray(codes));
            return target;
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * 读取兑换码文件
     */
    private JSONArray readVipCodeFile() {
        try {
            org.springframework.core.io.Resource resource = resourceLoader.getResource("classpath:biz/vipCode.json");
            String content = FileUtil.readString(resource.getFile(), StandardCharsets.UTF_8);
            return JSONUtil.parseArray(content);
        } catch (IOException e) {
            log.error("读取兑换码文件失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙");
        }
    }

    /**
     * 写入兑换码文件
     */
    private void writeVipCodeFile(JSONArray jsonArray) {
        try {
            org.springframework.core.io.Resource resource = resourceLoader.getResource("classpath:biz/vipCode.json");
            FileUtil.writeString(jsonArray.toStringPretty(), resource.getFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("更新兑换码文件失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙");
        }
    }

    /**
     * 更新用户会员信息
     */
    private void updateUserVipInfo(User user, String usedVipCode) {
        // 计算过期时间（当前时间 + 1 年）
        Date expireTime = DateUtil.offsetMonth(new Date(), 12); // 计算当前时间加 1 年后的时间

        // 构建更新对象
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setVipExpireTime(expireTime); // 设置过期时间
        updateUser.setVipCode(usedVipCode);     // 记录使用的兑换码
        updateUser.setUserRole(VIP_ROLE);       // 修改用户角色

        // 执行更新
        boolean updated = this.updateById(updateUser);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "开通会员失败，操作数据库失败");
        }
    }

    // endregion ------- 以下代码为用户兑换会员功能 --------

    private void getNewApiKey(Long userId)
    {
        CreateTokenRequest createTokenRequest = new CreateTokenRequest();
        createTokenRequest.setName(userId.toString());
        createTokenRequest.setRemain_quota(1L);
        newApiClient.createToken(createTokenRequest);
        sleep(2000);
        ApiResponse<List<TokenInfo>> tokenList = newApiClient.searchToken(userId.toString());
        TokenInfo token = tokenList.getData().getFirst();
        AccessKey accessKey = new AccessKey();
        accessKey.setApiKeyId(token.getId());
        accessKey.setUserId(userId);
        accessKey.setApiKey(token.getKey());
        accessKey.setCdKey(UUID.randomUUID().toString());
        accessKeyService.save(accessKey);
    }

}
