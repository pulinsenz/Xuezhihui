package com.agent.rag.controller;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.Result;
import com.agent.rag.dto.req.ChatSaveRequest;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.service.ChatHistoryService;
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
 * 内部接口：Python Agent 对话持久化回调。
 * <p>
 * 信任边界：Python worker 不直连 MySQL，每轮对话结果经此落库（会话 + 消息）；
 * 不走 JWT 拦截器（/internal/** 已放行），改由 X-Agent-Token 双向鉴权。
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/internal/agent")
public class ChatCallbackController {

    @Resource
    private ChatHistoryService chatHistoryService;

    @Value("${app.agent.token:}")
    private String agentToken;

    /**
     * 对话持久化回调（Python 每轮对话后调用，落库会话 + 两条消息）
     */
    @PostMapping("/chat-save")
    public Result<Boolean> chatSave(@RequestHeader(value = "X-Agent-Token", required = false) String token,
                                    @RequestBody ChatSaveRequest request) {
        if (!InternalAuthUtil.check(agentToken, token)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "内部接口未授权");
        }
        if (request == null || StrUtil.isBlank(request.getSessionId())
                || StrUtil.isBlank(request.getUserId()) || request.getAnswer() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话回调参数错误");
        }
        chatHistoryService.saveTurn(
                request.getSessionId(),
                Long.valueOf(request.getUserId()),
                request.getQuery(),
                request.getAnswer());
        return Result.success(true);
    }
}
