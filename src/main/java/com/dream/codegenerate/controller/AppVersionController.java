package com.dream.codegenerate.controller;

import com.dream.codegenerate.common.DeleteRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionCompareRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionRestoreRequest;
import com.dream.codegenerate.model.dto.appVersion.AppVersionSaveRequest;
import com.dream.codegenerate.model.vo.appVersion.AppVersionCompareVO;
import com.dream.codegenerate.model.vo.appVersion.AppVersionVO;
import com.mybatisflex.core.paginate.Page;
import com.dream.codegenerate.common.BaseResponse;
import com.dream.codegenerate.common.ResultUtils;
import com.dream.codegenerate.model.dto.appVersion.AppVersionQueryRequest;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.vo.appVersion.AppVersionQueryVO;
import com.dream.codegenerate.service.UserService;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.dream.codegenerate.service.AppVersionService;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用版本 控制层。
 *
 * dream
 */
@RestController
@RequestMapping("/appVersion")
public class AppVersionController {

    @Autowired
    private AppVersionService appVersionService;

    @Resource
    private UserService userService;

    /**
     * 保存应用版本。
     * @param AppVersionSaveRequest 应用版本
     * @return
     */
    @PostMapping("save")
    @ApiOperation("保存应用版本")
    public BaseResponse<Long> save(@RequestBody AppVersionSaveRequest AppVersionSaveRequest,HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.createNewVersion(AppVersionSaveRequest, loginUser));
    }
    /**
     * 查询所有应用版本。
     *
     * @return 所有数据
     */
    @PostMapping("list")
    @ApiOperation("查询应用的所有版本")
    public BaseResponse<Page<AppVersionQueryVO>> list(@RequestBody AppVersionQueryRequest appVersionQueryRequest,
                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.listByPage(appVersionQueryRequest, loginUser));
    }

    /**
     * 根据主键获取应用版本。
     *
     * @param id 应用版本主键
     * @return 应用版本详情
     */
    @GetMapping("getInfo/{id}")
    @ApiOperation("获取应用版本详情")
    public BaseResponse<AppVersionVO> getInfo(@PathVariable Long id,  HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.getAppVersionVOById(id, loginUser));
    }

    /**
     * 恢复应用版本
     *
     * @param appVersionRestoreRequest 版本恢复请求
     * @param request HTTP请求
     * @return 恢复的版本内容
     */
    @PostMapping("/restore")
    @ApiOperation("恢复应用版本")
    public BaseResponse<Boolean> restore(@RequestBody AppVersionRestoreRequest appVersionRestoreRequest,
                                                     HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.restore(appVersionRestoreRequest, loginUser));
    }

    /**
     * @description: 对比两个版本的差异
     * @author womon
     * @date 2025/9/22 19:55
     * @version 1.0
     */

    @PostMapping("/compare")
    @ApiOperation("对比两个版本的差异")
    public BaseResponse<AppVersionCompareVO> compare(@RequestBody AppVersionCompareRequest appVersionCompareRequest,
                                                     HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.compare(appVersionCompareRequest, loginUser));
    }


    @PostMapping("/delete")
    @ApiOperation("删除应用版本")
    public BaseResponse<Boolean> deleteById(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appVersionService.deleteByAppId(deleteRequest.getId(), loginUser));
    }
}
