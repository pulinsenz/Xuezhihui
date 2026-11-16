package com.agent.rag.controller;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.Result;
import com.agent.rag.dto.req.WikiCallbackRequest;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.service.WikiService;
import com.agent.rag.util.InternalAuthUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：Python Agent Wiki 生成完成回调。
 * <p>
 * 信任边界：Python worker 不直连 MySQL，生成的页面经此回写 wiki_page；
 * 不走 JWT 拦截器（/internal/** 已放行），改由 X-Agent-Token 双向鉴权。
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/internal/agent")
public class WikiCallbackController {

    @Resource
    private WikiService wikiService;

    @Value("${app.agent.token:}")
    private String agentToken;

    /**
     * Wiki 生成完成回调（Java 校验 X-Agent-Token 后回写 MySQL wiki_page）
     */
    @PostMapping("/wiki-callback")
    public Result<Boolean> wikiCallback(@RequestHeader(value = "X-Agent-Token", required = false) String token,
                                        @RequestBody WikiCallbackRequest request) {
        if (!InternalAuthUtil.check(agentToken, token)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "内部接口未授权");
        }
        wikiService.completeWikiCallback(request);
        return Result.success(true);
    }
}
