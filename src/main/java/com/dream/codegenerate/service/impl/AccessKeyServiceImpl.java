package com.dream.codegenerate.service.impl;

import com.dream.codegenerate.exception.BusinessException;
import com.dream.codegenerate.exception.ErrorCode;
import com.dream.codegenerate.exception.ThrowUtils;
import com.dream.codegenerate.model.entity.AccessKey;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.newapi.NewApiClient;
import com.dream.codegenerate.newapi.common.ApiResponse;
import com.dream.codegenerate.newapi.model.tokrn.request.UpdateTokenRequest;
import com.dream.codegenerate.newapi.model.tokrn.response.UpdateTokenResponseData;
import com.dream.codegenerate.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.dream.codegenerate.mapper.AccessKeyMapper;
import com.dream.codegenerate.service.AccessKeyService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import static com.dream.codegenerate.model.entity.table.AccessKeyTableDef.ACCESS_KEY;

/**
 * 访问key 服务层实现。
 *
 * @author dream
 */
@Service
public class AccessKeyServiceImpl extends ServiceImpl<AccessKeyMapper, AccessKey>  implements AccessKeyService{


    @Resource
    @Lazy
    private UserService userService;

    @Resource
    private NewApiClient newApiClient;
    @Override
    public String getApiKey(Long userId) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.eq(AccessKey::getUserId, userId);
        //查询数据库
        AccessKey accessKey = this.getOne(wrapper);
        return accessKey.getApiKey();
    }

    @Override
    public String getCdKey(HttpServletRequest request) {
        // 1. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        // 2. 检查用户兑换码
        // 使用 MyBatis-Flex 的 QueryWrapper 和静态 TableDef
        QueryWrapper userKeyQuery = QueryWrapper.create()
                .where(ACCESS_KEY.USER_ID.eq(userId));
        AccessKey existingKey = this.getOne(userKeyQuery);

        // 3. 返回兑换码
        return existingKey.getCdKey();
    }

    @Override
    public AccessKey getInfo(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.eq(AccessKey::getUserId, userId);
        AccessKey one = this.getOne(wrapper);
        if(one!=null&&one.getIsUse()==0)
        {
            one.setCdKey(null);
        }
        return one;
    }

    //使用兑换码
    @Override
    public boolean useCdKey(HttpServletRequest request) {
        // 1. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        // 2. 检查用户兑换码
        // 使用 MyBatis-Flex 的 QueryWrapper 和静态 TableDef
        QueryWrapper userKeyQuery = QueryWrapper.create()
                .where(ACCESS_KEY.USER_ID.eq(userId));
        AccessKey existingKey = this.getOne(userKeyQuery);
        // 如果 existingKey 不为 null 且 cdKey 字段有内容，则说明已使用
        ThrowUtils.throwIf(existingKey != null && existingKey.getIsUse()!=0,
                ErrorCode.OPERATION_ERROR, "您已经使用过兑换码，请勿重复使用");

        // 5. 将这个兑换码分配给当前用户
        // 使用 MyBatis-Flex 的 UpdateChain，代码更优雅
        boolean updateResult = UpdateChain.of(AccessKey.class)
                .set(ACCESS_KEY.IS_USE, 1)
                .set(ACCESS_KEY.USER_ID, userId)
                .where(ACCESS_KEY.ID.eq(existingKey.getId()))
                .update();

        // 6. 检查更新是否成功
        if (!updateResult) {
            // 如果更新失败，可能意味着在极端的并发情况下出现了问题，回滚事务
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "兑换码分配失败，请稍后重试");
        }
        UpdateTokenRequest updateTokenRequest = new UpdateTokenRequest();
        updateTokenRequest.setName(String.valueOf(userId));
        updateTokenRequest.setId(existingKey.getApiKeyId());
        updateTokenRequest.setRemain_quota(500000L);
        ApiResponse<UpdateTokenResponseData> updateTokenResponseDataApiResponse = newApiClient.updateToken(updateTokenRequest);
        return updateTokenResponseDataApiResponse.isSuccess();
    }
}


