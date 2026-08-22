package com.agent.rag.service;

import com.agent.rag.dto.req.ChatRequest;
import com.agent.rag.dto.resp.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话服务
 *
 * @author pulinsenz
 */
public interface ChatService {

    /**
     * 普通对话（非流式）
     */
    ChatResponse chat(ChatRequest request);

    /**
     * SSE 流式对话：透传 Python Agent 的 token 流给前端
     */
    SseEmitter stream(ChatRequest request);
}
