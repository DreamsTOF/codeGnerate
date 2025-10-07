package com.dream.codegenerate.controller;

import com.dream.codegenerate.common.BaseResponse;
import com.dream.codegenerate.common.ResultUtils;
import com.dream.codegenerate.model.entity.AccessKey;
import com.dream.codegenerate.model.vo.AccessKeyVo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.dream.codegenerate.service.AccessKeyService;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问key 控制层。
 *
 * @author dream
 */
@RestController
@RequestMapping("/accessKey")
public class AccessKeyController {

    @Autowired
    private AccessKeyService accessKeyService;
    /**
     * 获取访问兑换码。
     *
     * @return 兑换码
     */
    @GetMapping("cdKey")
    public BaseResponse<String> getCdKey(HttpServletRequest  request) {
        return ResultUtils.success(accessKeyService.getCdKey(request));
    }

    /**
     * 获取key详细信息。
     *
     * @return key详细信息
     */
    @GetMapping("getInfo")
    public BaseResponse<AccessKeyVo> getApiKey(HttpServletRequest  request) {

        return ResultUtils.success(accessKeyService.getInfo(request));
    }
    /**
     * 使用兑换码
     *
     */
    @PutMapping("useCdKey")
    public BaseResponse<Boolean> useCdKey(HttpServletRequest  request,String cdKey) {
        return ResultUtils.success(accessKeyService.useCdKey(request,cdKey));
    }

}
