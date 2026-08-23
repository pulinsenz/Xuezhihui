package com.agent.rag.controller;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.Result;
import com.agent.rag.dto.req.VectorizeCallbackRequest;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.service.TaskService;
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
 * 内部接口：Python Agent 向量化任务完成回调。
 * <p>
 * 信任边界：Python worker 不直连 MySQL，任务结果（SUCCESS/FAILED）经此回写文档状态；
 * 不走 JWT 拦截器（/internal/** 已放行），改由 X-Agent-Token 双向鉴权。
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/internal/agent")
public class VectorCallbackController {

    @Resource
    private TaskService taskService;

    @Value("${app.agent.token:}")
    private String agentToken;

    /**
     * 向量化任务完成回调（Java 校验 X-Agent-Token 后回写 MySQL 文档状态）
     */
    @PostMapping("/vector-callback")
    public Result<Boolean> vectorCallback(@RequestHeader(value = "X-Agent-Token", required = false) String token,
                                          @RequestBody VectorizeCallbackRequest request) {
        if (!InternalAuthUtil.check(agentToken, token)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "内部接口未授权");
        }
        taskService.completeVectorize(request);
        return Result.success(true);
    }
}
