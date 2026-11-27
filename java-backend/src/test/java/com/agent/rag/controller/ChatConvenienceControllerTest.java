package com.agent.rag.controller;

import com.agent.rag.service.ChatService;
import com.agent.rag.service.KnowledgeQuestionsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChatConvenienceControllerTest {
    @Mock private ChatService chatService;
    @Mock private KnowledgeQuestionsService questionsService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ChatController chat = new ChatController();
        ReflectionTestUtils.setField(chat, "chatService", chatService);
        KnowledgeQuestionsController questions = new KnowledgeQuestionsController();
        ReflectionTestUtils.setField(questions, "service", questionsService);
        mvc = MockMvcBuilders.standaloneSetup(chat, questions).build();
    }

    @Test
    void pinAndFeedbackUseExpectedPathsAndPreserveLargeId() throws Exception {
        mvc.perform(put("/chat/sessions/s1/pin").contentType(MediaType.APPLICATION_JSON).content("{\"pinned\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(chatService).pinSession("s1", true);
        mvc.perform(put("/chat/sessions/s1/messages/9007199254740993/feedback")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":-1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(chatService).rateMessage("s1", 9007199254740993L, -1);
    }

    @Test
    void questionsGetAndPutUseArrayContract() throws Exception {
        when(questionsService.list(5L)).thenReturn(List.of("如何考核？"));
        mvc.perform(get("/knowledge/5/questions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("如何考核？"));
        mvc.perform(put("/knowledge/5/questions").contentType(MediaType.APPLICATION_JSON).content("{\"questions\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        verify(questionsService).save(5L, List.of());
    }
}
