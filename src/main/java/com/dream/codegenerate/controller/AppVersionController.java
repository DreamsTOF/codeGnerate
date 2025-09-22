package com.dream.codegenerate.controller;

import com.mybatisflex.core.paginate.Page;
import com.dream.codegenerate.common.BaseResponse;
import com.dream.codegenerate.common.ResultUtils;
import com.dream.codegenerate.model.dto.appVersion.AppVersionQueryRequest;
import com.dream.codegenerate.model.entity.User;
import com.dream.codegenerate.model.vo.appVersion.AppVersionQueryVO;
import com.dream.codegenerate.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.dream.codegenerate.model.entity.AppVersion;
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
     *
     * @param appVersion 应用版本
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public boolean save(@RequestBody AppVersion appVersion) {
        return appVersionService.save(appVersion);
    }

//    /**
//     * 根据主键删除应用版本。
//     *
//     * @param id 主键
//     * @return {@code true} 删除成功，{@code false} 删除失败
//     */
//    @DeleteMapping("remove/{id}")
//    public boolean remove(@PathVariable Long id) {
//        return appVersionService.removeById(id);
//    }
//
//    /**
//     * 根据主键更新应用版本。
//     *
//     * @param appVersion 应用版本
//     * @return {@code true} 更新成功，{@code false} 更新失败
//     */
//    @PutMapping("update")
//    public boolean update(@RequestBody AppVersion appVersion) {
//        return appVersionService.updateById(appVersion);
//    }

    /**
     * 查询所有应用版本。
     *
     * @return 所有数据
     */
    @GetMapping("list")
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
    public AppVersion getInfo(@PathVariable Long id) {
        return appVersionService.getById(id);
    }


}
