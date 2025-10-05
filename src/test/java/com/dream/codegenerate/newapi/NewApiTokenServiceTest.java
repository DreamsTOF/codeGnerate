package com.dream.codegenerate.newapi;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.dream.codegenerate.constant.AppConstant;
import com.dream.codegenerate.newapi.common.ApiResponse;
import com.dream.codegenerate.newapi.model.tokrn.request.CreateTokenRequest;
import com.dream.codegenerate.newapi.model.tokrn.request.UpdateTokenRequest;
import com.dream.codegenerate.newapi.model.tokrn.response.TokenInfo;
import com.dream.codegenerate.newapi.model.tokrn.response.UpdateTokenResponseData;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

@SpringBootTest
class NewApiTokenServiceTest {

    @Resource
    private NewApiClient newApiClient;
    @Test
    void createToken() {
        CreateTokenRequest createTokenRequest = new CreateTokenRequest();
        createTokenRequest.setName("12345678");
        createTokenRequest.setRemain_quota(100000L);
        ApiResponse<Object> token = newApiClient.createToken(createTokenRequest);
        assert token.isSuccess();
    }

    @Test
    void searchToken() {
        ApiResponse<TokenInfo> token = newApiClient.searchToken("12345678");
    }

    @Test
    void updateToken() {
        UpdateTokenRequest updateTokenRequest = new UpdateTokenRequest();
        updateTokenRequest.setId(11L);
        updateTokenRequest.setRemain_quota(100000L);
        ApiResponse<UpdateTokenResponseData> token = newApiClient.updateToken(updateTokenRequest);
        assert token.isSuccess();
    }


}
