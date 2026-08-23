package com.agent.rag.service;

import com.agent.rag.dto.resp.HistoryMessageVO;
import com.agent.rag.dto.resp.SessionVO;

import java.util.List;

/**
 * 对话历史服务：会话/消息持久化（MySQL），按用户隔离
 *
 * @author pulinsenz
 */
public interface ChatHistoryService {

    /**
     * 保存一轮对话（Python 回调）：会话存在则更新时间，否则创建（标题取首条用户消息）；
     * 追加 user + assistant 两条消息
     */
    void saveTurn(String sessionId, Long userId, String query, String answer);

    /**
     * 当前用户的会话列表（按最近活跃倒序）
     */
    List<SessionVO> listSessions(Long userId);

    /**
     * 会话完整历史（校验属主，非本人/不存在抛 NOT_FOUND）
     */
    List<HistoryMessageVO> getHistory(String sessionId, Long userId);

    /**
     * 删除会话及其消息（仅本人）
     */
    void deleteSession(String sessionId, Long userId);

    /**
     * 写侧越权防护：会话已存在但属主不是该用户 → 抛 NO_AUTH；会话不存在（新会话）放行
     */
    void checkAccess(String sessionId, Long userId);
}
