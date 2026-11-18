package com.agent.rag.service;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.controller.AgentDataController;
import com.agent.rag.entity.*;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.*;
import com.agent.rag.service.impl.ChatHistoryServiceImpl;
import com.agent.rag.service.impl.KnowledgeServiceImpl;
import com.agent.rag.util.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentEvidenceServiceTest {
    private AgentEvidenceService service;
    private KnowledgeServiceImpl knowledgeService;
    private KnowledgeMapper knowledgeMapper;
    private KnowledgeMemberMapper memberMapper;
    private KnowledgeFavoriteMapper favoriteMapper;
    private KnowledgeDocMapper docMapper;
    private WikiPageMapper pageMapper;
    private ChatHistoryService history;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        knowledgeMapper = mock(KnowledgeMapper.class);
        memberMapper = mock(KnowledgeMemberMapper.class);
        favoriteMapper = mock(KnowledgeFavoriteMapper.class);
        docMapper = mock(KnowledgeDocMapper.class);
        pageMapper = mock(WikiPageMapper.class);
        userMapper = mock(UserMapper.class);
        history = mock(ChatHistoryService.class);
        knowledgeService = new KnowledgeServiceImpl();
        ReflectionTestUtils.setField(knowledgeService, "knowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(knowledgeService, "knowledgeMemberMapper", memberMapper);
        ReflectionTestUtils.setField(knowledgeService, "knowledgeFavoriteMapper", favoriteMapper);
        ReflectionTestUtils.setField(knowledgeService, "knowledgeDocMapper", docMapper);
        service = new AgentEvidenceService();
        ReflectionTestUtils.setField(service, "knowledgeService", knowledgeService);
        ReflectionTestUtils.setField(service, "chatHistoryService", history);
        ReflectionTestUtils.setField(service, "knowledgeDocMapper", docMapper);
        ReflectionTestUtils.setField(service, "wikiPageMapper", pageMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        User user = new User();
        user.setId(1L);
        user.setUserRole("user");
        when(userMapper.selectById(1L)).thenReturn(user);
    }

    @AfterEach
    void clear() { UserContext.clear(); }

    private Knowledge knowledge(boolean isPublic) {
        Knowledge k = new Knowledge();
        k.setId(5L);
        k.setUserId(2L);
        k.setIsPublic(isPublic ? 1 : 0);
        when(knowledgeMapper.selectById(5L)).thenReturn(k);
        return k;
    }

    private void emptyDirectory() {
        when(pageMapper.selectList(any())).thenReturn(List.of());
        when(docMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void privateUnauthorizedFailsBeforeReadingDirectory() {
        knowledge(false);
        BusinessException error = assertThrows(BusinessException.class, () -> service.knowledgeOutline(1L, 5L));
        assertEquals(ErrorCode.NO_AUTH.getCode(), error.getCode());
        verifyNoInteractions(pageMapper, docMapper);
        assertNull(UserContext.getUser());
    }

    @Test
    void publicLibraryReturnsRealEmptyResult() {
        knowledge(true);
        emptyDirectory();
        Map<String, Object> result = service.knowledgeOutline(1L, 5L);
        assertEquals(List.of(), result.get("wiki_pages"));
        assertEquals(0, result.get("total"));
        assertEquals(64, result.get("version").toString().length());
    }

    @Test
    void collaboratorUsesExistingReadRule() {
        knowledge(false);
        when(memberMapper.selectCount(any())).thenReturn(1L);
        emptyDirectory();
        assertEquals(0, service.knowledgeOutline(1L, 5L).get("total"));
    }

    @Test
    void favoriteUsesExistingReadRule() {
        knowledge(false);
        when(favoriteMapper.selectCount(any())).thenReturn(1L);
        emptyDirectory();
        assertEquals(0, service.knowledgeOutline(1L, 5L).get("total"));
    }

    @Test
    void adminDoesNotGainNewPrivateLibraryBypass() {
        knowledge(false);
        User admin = new User();
        admin.setId(1L);
        admin.setUserRole("admin");
        when(userMapper.selectById(1L)).thenReturn(admin);
        assertThrows(BusinessException.class, () -> service.knowledgeOutline(1L, 5L));
    }

    @Test
    void ownerDirectoryUsesPageIdExcludesFailedAndDoesNotInventDocumentSummary() {
        knowledge(false).setUserId(1L);
        WikiPage page = new WikiPage();
        page.setId(17L);
        page.setKnowledgeId(5L);
        page.setTitle("主题");
        page.setSummary("已存摘要");
        page.setVectorDocId("wiki-old-vector-uuid");
        page.setSourceDocIds("[\"11\"]");
        WikiPage failed = new WikiPage();
        failed.setId(18L);
        failed.setErrorMsg("生成失败");
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(11L);
        doc.setName("课程.pdf");
        KnowledgeDoc uncovered = new KnowledgeDoc();
        uncovered.setId(12L);
        uncovered.setName("附录.txt");
        when(pageMapper.selectList(any())).thenReturn(List.of(page, failed));
        when(docMapper.selectList(any())).thenReturn(List.of(doc, uncovered));
        Map<String, Object> result = service.knowledgeOutline(1L, 5L);
        assertEquals(1, result.get("total"));
        assertEquals(1L, result.get("uncovered_document_count"));
        List<?> pages = (List<?>) result.get("wiki_pages");
        assertEquals("wiki-17", ((Map<?, ?>) pages.get(0)).get("doc_id"));
        List<?> docs = (List<?>) result.get("documents");
        assertNull(((Map<?, ?>) docs.get(0)).get("summary"));
        String version = result.get("version").toString();
        page.setSummary("更新后的摘要");
        assertNotEquals(version, service.knowledgeOutline(1L, 5L).get("version"));
    }

    @Test
    void failedLookupIsNotReportedAsEmptyDirectoryAndContextIsRestored() {
        User previous = new User();
        previous.setId(99L);
        UserContext.setUser(previous);
        assertThrows(BusinessException.class, () -> service.knowledgeOutline(1L, 5L));
        assertSame(previous, UserContext.getUser());
    }

    private ChatMessage message(String sources) {
        ChatMessage message = new ChatMessage();
        message.setId(42L);
        message.setRole("assistant");
        message.setContent("历史正文");
        message.setKnowledgeId("5");
        message.setSources(sources);
        when(history.getEvidenceMessages("session", 1L)).thenReturn(List.of(message));
        return message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evidence() {
        return ((List<Map<String, Object>>) service.conversationEvidence(1L, "session").get("messages")).get(0);
    }

    @Test
    void historySourcesAreParsedAuthorizedAndKeepStableMessageId() {
        knowledge(true);
        message("[{\"doc_id\":\"11\",\"text\":\"引用原文\"}]");
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(11L);
        doc.setKnowledgeId(5L);
        when(docMapper.selectById(11L)).thenReturn(doc);
        Map<String, Object> result = evidence();
        assertEquals("42", result.get("id"));
        assertEquals("历史正文", result.get("content"));
        assertEquals(false, result.get("access_revoked"));
        assertEquals(1, ((List<?>) result.get("sources")).size());
    }

    @Test
    void revokedHistoryDoesNotExposeSourceOrAnswerContent() {
        knowledge(false);
        message("[{\"doc_id\":\"11\",\"text\":\"私密原文\"}]");
        Map<String, Object> result = evidence();
        assertEquals(true, result.get("access_revoked"));
        assertEquals("", result.get("content"));
        assertEquals(List.of(), result.get("sources"));
    }
    @Test
    void deletedDocumentSourceIsNotExposed() {
        knowledge(true);
        message("[{\"doc_id\":\"11\",\"text\":\"已删除文档原文\"}]");
        Map<String, Object> result = evidence();
        assertEquals(true, result.get("access_revoked"));
        assertEquals(List.of(), result.get("sources"));
        assertEquals("", result.get("content"));
    }


    @Test
    void legacyVectorWikiSourceResolvesToActualPageId() {
        knowledge(true);
        message("[{\"doc_id\":\"wiki-uuid\",\"text\":\"引用\"}]");
        WikiPage page = new WikiPage();
        page.setId(17L);
        page.setKnowledgeId(5L);
        when(pageMapper.selectList(any())).thenReturn(List.of(page));
        List<?> sources = (List<?>) evidence().get("sources");
        assertEquals("wiki-17", ((Map<?, ?>) sources.get(0)).get("doc_id"));
    }

    @Test
    void malformedHistorySourcesFailExplicitly() {
        message("not-json");
        assertThrows(BusinessException.class, this::evidence);
    }

    @Test
    void realHistoryServiceRejectsOtherOwnersAndMissingSessions() {
        ChatHistoryServiceImpl realHistory = new ChatHistoryServiceImpl();
        ChatSessionMapper sessions = mock(ChatSessionMapper.class);
        ChatMessageMapper messages = mock(ChatMessageMapper.class);
        ReflectionTestUtils.setField(realHistory, "chatSessionMapper", sessions);
        ReflectionTestUtils.setField(realHistory, "chatMessageMapper", messages);
        ChatSession session = new ChatSession();
        session.setUserId(2L);
        when(sessions.selectById("other")).thenReturn(session);
        assertThrows(BusinessException.class, () -> realHistory.getEvidenceMessages("other", 1L));
        assertThrows(BusinessException.class, () -> realHistory.getEvidenceMessages("missing", 1L));
        verifyNoInteractions(messages);
        session.setUserId(1L);
        when(messages.selectList(any())).thenReturn(List.of());
        assertEquals(List.of(), realHistory.getEvidenceMessages("other", 1L));
    }

    @Test
    void endpointsRequireInternalTokenBeforeReading() {
        AgentDataController controller = new AgentDataController();
        AgentEvidenceService mockService = mock(AgentEvidenceService.class);
        ReflectionTestUtils.setField(controller, "agentToken", "secret");
        ReflectionTestUtils.setField(controller, "agentEvidenceService", mockService);
        assertThrows(BusinessException.class, () -> controller.knowledgeOutline(1L, 5L, null));
        assertThrows(BusinessException.class, () -> controller.conversationEvidence(1L, "session", "bad"));
        verifyNoInteractions(mockService);
        when(mockService.knowledgeOutline(1L, 5L)).thenReturn(Map.of());
        controller.knowledgeOutline(1L, 5L, "secret");
        verify(mockService).knowledgeOutline(1L, 5L);
    }
}
