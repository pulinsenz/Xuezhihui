package com.agent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.dto.resp.HistoryMessageVO;
import com.agent.rag.dto.resp.SessionVO;
import com.agent.rag.entity.ChatMessage;
import com.agent.rag.entity.ChatSession;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.ChatMessageMapper;
import com.agent.rag.mapper.ChatSessionMapper;
import com.agent.rag.service.ChatHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 对话历史服务实现：MySQL 持久化（唯一事实源），Redis 仅作 LLM 上下文
 * <p>
 * assistant 消息额外持久化回答属性(route/knowledgeId)、思考过程(thinking)、参考文献(sources)
 * 为 JSON 字符串，重载历史时还原展示。
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final int TITLE_MAX = 20;
    private static final String DEFAULT_TITLE = "新对话";

    /**
     * 手动重命名的标题上限，与 chat_session.title 列宽（varchar(64)）一致
     */
    private static final int TITLE_RENAME_MAX = 64;

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public void saveTurn(String sessionId, Long userId, String query, String answer,
                         String route, String knowledgeId, List<String> thinking, List<Map<String, Object>> sources) {
        if (StrUtil.isBlank(sessionId) || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话参数错误");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null) {
            ChatSession created = new ChatSession();
            created.setSessionId(sessionId);
            created.setUserId(userId);
            created.setTitle(buildTitle(query));
            created.setCreateTime(now);
            created.setUpdateTime(now);
            chatSessionMapper.insert(created);
        } else {
            ChatSession update = new ChatSession();
            update.setSessionId(sessionId);
            update.setUpdateTime(now);
            chatSessionMapper.updateById(update);
        }
        insertMessage(sessionId, userId, "user", query, now, null, null, null, null);
        insertMessage(sessionId, userId, "assistant", answer, now,
                route, knowledgeId, toJson(thinking), toJson(sources));
    }

    @Override
    public List<SessionVO> listSessions(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 id 不能为空");
        }
        List<ChatSession> sessions = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdateTime));
        return sessions.stream().map(session -> {
            SessionVO vo = new SessionVO();
            vo.setSessionId(session.getSessionId());
            vo.setTitle(session.getTitle());
            vo.setUpdateTime(session.getUpdateTime() == null
                    ? null
                    : session.getUpdateTime().atZone(ZoneId.systemDefault()).toEpochSecond());
            vo.setMessageCount(chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getSessionId, session.getSessionId())));
            return vo;
        }).toList();
    }

    @Override
    public List<HistoryMessageVO> getHistory(String sessionId, Long userId) {
        requireOwned(sessionId, userId);
        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
        return messages.stream().map(m -> {
            HistoryMessageVO vo = HistoryMessageVO.of(m.getRole(), m.getContent());
            vo.setRoute(m.getRoute());
            vo.setKnowledgeId(m.getKnowledgeId());
            vo.setThinking(parseStringList(m.getThinking()));
            vo.setSources(parseMapList(m.getSources()));
            return vo;
        }).toList();
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId, Long userId) {
        requireOwned(sessionId, userId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        chatSessionMapper.deleteById(sessionId);
        log.info("删除会话: sessionId={}, userId={}", sessionId, userId);
    }

    @Override
    public void renameSession(String sessionId, Long userId, String title) {
        if (StrUtil.isBlank(sessionId) || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话参数错误");
        }
        String name = title == null ? "" : title.trim();
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话名称不能为空");
        }
        if (name.length() > TITLE_RENAME_MAX) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话名称不能超过 64 个字符");
        }
        ChatSession session = requireOwned(sessionId, userId);
        ChatSession update = new ChatSession();
        update.setSessionId(sessionId);
        update.setTitle(name);
        // 显式回写原 updateTime，抵消列的 ON UPDATE CURRENT_TIMESTAMP：重命名不应改变最近活跃排序
        update.setUpdateTime(session.getUpdateTime());
        chatSessionMapper.updateById(update);
        log.info("重命名会话: sessionId={}, userId={}, title={}", sessionId, userId, name);
    }

    @Override
    public void checkAccess(String sessionId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        if (StrUtil.isBlank(sessionId)) {
            return; // 空 session 由上层生成
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        // 会话不存在视为新会话放行（首条消息由回调落库）；存在则必须属主一致
        if (session != null && !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权使用该会话");
        }
    }

    /**
     * 会话必须存在且属主为该用户，否则 NOT_FOUND（对外不暴露存在性）；校验通过返回该会话
     */
    private ChatSession requireOwned(String sessionId, Long userId) {
        if (StrUtil.isBlank(sessionId) || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话参数错误");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    private void insertMessage(String sessionId, Long userId, String role, String content, LocalDateTime now,
                               String route, String knowledgeId, String thinkingJson, String sourcesJson) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(now);
        message.setRoute(route);
        message.setKnowledgeId(knowledgeId);
        message.setThinking(thinkingJson);
        message.setSources(sourcesJson);
        chatMessageMapper.insert(message);
    }

    private String buildTitle(String query) {
        if (StrUtil.isBlank(query)) {
            return DEFAULT_TITLE;
        }
        String text = query.trim().replaceAll("\\s+", " ");
        return text.length() <= TITLE_MAX ? text : text.substring(0, TITLE_MAX);
    }

    // ---- JSON 序列化/反序列化（thinking、sources 存 JSON 字符串） ----

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parseStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("JSON 解析失败(thinking): {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> parseMapList(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            log.warn("JSON 解析失败(sources): {}", e.getMessage());
            return null;
        }
    }
}
