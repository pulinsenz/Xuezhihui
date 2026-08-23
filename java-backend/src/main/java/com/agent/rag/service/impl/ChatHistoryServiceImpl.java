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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 对话历史服务实现：MySQL 持久化（唯一事实源），Redis 仅作 LLM 上下文
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final int TITLE_MAX = 20;
    private static final String DEFAULT_TITLE = "新对话";

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Override
    @Transactional
    public void saveTurn(String sessionId, Long userId, String query, String answer) {
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
        insertMessage(sessionId, userId, "user", query, now);
        insertMessage(sessionId, userId, "assistant", answer, now);
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
        return messages.stream()
                .map(m -> HistoryMessageVO.of(m.getRole(), m.getContent()))
                .toList();
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
     * 会话必须存在且属主为该用户，否则 NOT_FOUND（对外不暴露存在性）
     */
    private void requireOwned(String sessionId, Long userId) {
        if (StrUtil.isBlank(sessionId) || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话参数错误");
        }
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        }
    }

    private void insertMessage(String sessionId, Long userId, String role, String content, LocalDateTime now) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(now);
        chatMessageMapper.insert(message);
    }

    private String buildTitle(String query) {
        if (StrUtil.isBlank(query)) {
            return DEFAULT_TITLE;
        }
        String text = query.trim().replaceAll("\\s+", " ");
        return text.length() <= TITLE_MAX ? text : text.substring(0, TITLE_MAX);
    }
}
