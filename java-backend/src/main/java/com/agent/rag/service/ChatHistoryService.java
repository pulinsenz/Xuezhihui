package com.agent.rag.service;

import com.agent.rag.dto.resp.HistoryMessageVO;
import com.agent.rag.dto.resp.SessionVO;

import java.util.List;
import java.util.Map;

/**
 * 对话历史服务：会话/消息持久化（MySQL），按用户隔离
 *
 * @author pulinsenz
 */
public interface ChatHistoryService {

    /**
     * 保存一轮对话（Python 回调）：会话存在则更新时间，否则创建（标题取首条用户消息）；
     * 追加 user + assistant 两条消息；assistant 消息记录回答属性/思考过程/参考文献
     */
    void saveTurn(String sessionId, Long userId, String query, String answer,
                  String route, String knowledgeId, List<String> thinking, List<Map<String, Object>> sources);

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
