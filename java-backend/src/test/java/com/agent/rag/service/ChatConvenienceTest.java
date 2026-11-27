package com.agent.rag.service;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.entity.ChatMessage;
import com.agent.rag.entity.ChatSession;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.ChatMessageMapper;
import com.agent.rag.mapper.ChatSessionMapper;
import com.agent.rag.service.impl.ChatHistoryServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatConvenienceTest {
    @Mock private ChatSessionMapper sessions;
    @Mock private ChatMessageMapper messages;
    private ChatHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(false);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ChatSession.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ChatMessage.class);
        service = new ChatHistoryServiceImpl();
        ReflectionTestUtils.setField(service, "chatSessionMapper", sessions);
        ReflectionTestUtils.setField(service, "chatMessageMapper", messages);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    private ChatSession session(long owner) {
        ChatSession s = new ChatSession();
        s.setSessionId("s1");
        s.setUserId(owner);
        s.setTitle("课程");
        s.setUpdateTime(LocalDateTime.of(2026, 11, 24, 12, 0));
        return s;
    }

    private ChatMessage message(String role, String sessionId, long owner) {
        ChatMessage m = new ChatMessage();
        m.setId(9007199254740993L);
        m.setSessionId(sessionId);
        m.setUserId(owner);
        m.setRole(role);
        m.setContent("回答");
        return m;
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void pinPreservesActivityAndConstrainsOwner() {
        when(sessions.selectById("s1")).thenReturn(session(1));
        service.pinSession("s1", 1L, true);
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(sessions).update(isNull(), query.capture());
        LambdaUpdateWrapper<ChatSession> update = (LambdaUpdateWrapper<ChatSession>) query.getValue();
        assertTrue(update.getSqlSet().contains("updateTime = updateTime"));
        assertTrue(update.getSqlSet().contains("pinned"));
        assertTrue(update.getSqlSegment().contains("userId"));
        assertTrue(update.getParamNameValuePairs().containsValue(true));
        assertTrue(update.getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void pinRejectsOtherUserAndMissingFlag() {
        when(sessions.selectById("s1")).thenReturn(session(2));
        BusinessException error = assertThrows(BusinessException.class, () -> service.pinSession("s1", 1L, true));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), error.getCode());
        assertThrows(BusinessException.class, () -> service.pinSession("s1", 1L, null));
        verify(sessions, never()).update(any(), any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void feedbackCanBeSetAndClearedWithOwnerConstraint() {
        when(sessions.selectById("s1")).thenReturn(session(1));
        when(messages.selectById(9L)).thenReturn(message("assistant", "s1", 1));
        for (int value : new int[]{1, -1, 0}) service.rateMessage("s1", 9L, 1L, value);
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(messages, times(3)).update(isNull(), query.capture());
        LambdaUpdateWrapper<ChatMessage> update = (LambdaUpdateWrapper<ChatMessage>) query.getAllValues().get(2);
        assertTrue(update.getSqlSet().contains("feedback"));
        assertTrue(update.getSqlSegment().contains("sessionId"));
        assertTrue(update.getParamNameValuePairs().containsValue(0));
    }

    @Test
    void rejectsCrossSessionCrossOwnerUserMessageAndInvalidRating() {
        when(sessions.selectById("s1")).thenReturn(session(1));
        when(messages.selectById(9L)).thenReturn(message("assistant", "s2", 1));
        assertThrows(BusinessException.class, () -> service.rateMessage("s1", 9L, 1L, 1));
        when(messages.selectById(9L)).thenReturn(message("assistant", "s1", 2));
        assertThrows(BusinessException.class, () -> service.rateMessage("s1", 9L, 1L, 1));
        when(messages.selectById(9L)).thenReturn(message("user", "s1", 1));
        assertThrows(BusinessException.class, () -> service.rateMessage("s1", 9L, 1L, 1));
        assertThrows(BusinessException.class, () -> service.rateMessage("s1", 9L, 1L, 2));
        assertThrows(BusinessException.class, () -> service.rateMessage("s1", 9L, 1L, null));
        verify(messages, never()).update(any(), any());
    }

    @Test
    void historyExposesExactStringIdAndFeedback() {
        when(sessions.selectById("s1")).thenReturn(session(1));
        ChatMessage message = message("assistant", "s1", 1);
        message.setFeedback(-1);
        when(messages.selectList(any())).thenReturn(List.of(message));
        var result = service.getHistory("s1", 1L).get(0);
        assertEquals("9007199254740993", result.getId());
        assertEquals(-1, result.getFeedback());
    }

    @Test
    void listQueriesPinnedBeforeActivityAndReturnsFlag() {
        ChatSession s = session(1);
        s.setPinned(true);
        when(sessions.selectList(any())).thenAnswer(call -> {
            Wrapper<?> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().matches("(?s).*ORDER BY pinned DESC,?updateTime DESC.*"));
            return List.of(s);
        });
        when(messages.selectMaps(any())).thenReturn(List.of(Map.of("sessionId", "s1", "cnt", 2L)));
        var result = service.listSessions(1L).get(0);
        assertTrue(result.getPinned());
        assertEquals(2L, result.getMessageCount());
    }
}
