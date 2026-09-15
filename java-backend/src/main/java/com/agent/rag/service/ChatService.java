package com.agent.rag.service;

import com.agent.rag.dto.req.ChatRequest;
import com.agent.rag.dto.resp.ChatResponse;
import com.agent.rag.dto.resp.HistoryMessageVO;
import com.agent.rag.dto.resp.SessionVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

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

    /**
     * 当前用户的会话列表
     */
    List<SessionVO> listSessions();

    /**
     * 会话完整历史（仅本人）
     */
    List<HistoryMessageVO> getSessionHistory(String sessionId);

    /**
     * 删除会话（仅本人）
     */
    void deleteSession(String sessionId);

    /**
     * 重命名会话（仅本人）
     */
    void renameSession(String sessionId, String title);
}

