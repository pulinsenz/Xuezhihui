package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.UpdateCollapseRefsRequest;
import com.agent.rag.dto.req.UpdateVectorizeDefaultRequest;
import com.agent.rag.dto.resp.SettingsVO;
import com.agent.rag.service.SettingsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户设置接口（需登录，/settings 已被 LoginInterceptor 覆盖）
 *
 * @author pulinsenz
 */
@RestController
@RequestMapping("/settings")
public class SettingsController {

    @Resource
    private SettingsService settingsService;

    /**
     * 当前用户设置
     */
    @GetMapping
    public Result<SettingsVO> getSettings() {
        return Result.success(settingsService.getSettings());
    }

    /**
     * 更新"上传文档是否默认入库"：1=是 0=否
     */
    @PutMapping("/vectorize-default")
    public Result<Boolean> updateVectorizeDefault(@RequestBody UpdateVectorizeDefaultRequest request) {
        settingsService.updateVectorizeDefault(request.getDefaultVectorize());
        return Result.success(true);
    }

    /**
     * 更新"参考文献默认折叠"：1=折叠 0=展开
     */
    @PutMapping("/collapse-refs")
    public Result<Boolean> updateCollapseRefs(@RequestBody UpdateCollapseRefsRequest request) {
        settingsService.updateCollapseRefs(request.getCollapseRefs());
        return Result.success(true);
    }
}
