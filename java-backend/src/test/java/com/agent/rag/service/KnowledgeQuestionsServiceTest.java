package com.agent.rag.service;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeQuestionsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeQuestionsServiceTest {
    @Mock private KnowledgeService knowledge;
    @Mock private KnowledgeQuestionsMapper mapper;
    private KnowledgeQuestionsService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeQuestionsService();
        ReflectionTestUtils.setField(service, "knowledgeService", knowledge);
        ReflectionTestUtils.setField(service, "mapper", mapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void savesTrimmedOrderedQuestionsAndCanClear() {
        service.save(5L, List.of(" 问题二 ", "问题一"));
        verify(knowledge).getOwnedKnowledge(5L);
        verify(mapper).saveQuestions(5L, "[\"问题二\",\"问题一\"]");
        service.save(5L, List.of());
        verify(mapper).saveQuestions(5L, "[]");
    }

    @Test
    void rejectsBlankDuplicatesTooLongTooManyAndNull() {
        for (List<String> value : Arrays.asList(List.of(" "), List.of("问题", " 问题 "),
                List.of("a".repeat(201)), Collections.nCopies(9, "问题"), Arrays.asList((String) null))) {
            assertThrows(BusinessException.class, () -> service.save(5L, value));
        }
        assertThrows(BusinessException.class, () -> service.save(5L, null));
        verify(mapper, never()).saveQuestions(anyLong(), anyString());
    }

    @Test
    void onlyOwnerCanSaveAndDeniedReadsNeverQueryStorage() {
        when(knowledge.getOwnedKnowledge(5L)).thenThrow(new BusinessException(ErrorCode.NO_AUTH, "无权操作"));
        assertThrows(BusinessException.class, () -> service.save(5L, List.of("问题")));
        when(knowledge.getViewableKnowledge(5L)).thenThrow(new BusinessException(ErrorCode.NO_AUTH, "无权查看"));
        assertThrows(BusinessException.class, () -> service.list(5L));
        verifyNoInteractions(mapper);
    }

    @Test
    void readsOrderedQuestionsAndDefaultsToEmpty() {
        when(mapper.findQuestions(5L)).thenReturn(null, "[\"第二\",\"第一\"]");
        assertEquals(List.of(), service.list(5L));
        assertEquals(List.of("第二", "第一"), service.list(5L));
        verify(knowledge, times(2)).getViewableKnowledge(5L);
    }
}
