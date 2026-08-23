package com.agent.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Python 对话落库回调接口测试（MockMvc + 真实 MySQL）
 * <p>
 * 覆盖：X-Agent-Token 鉴权（缺失/错误拒绝）、saveTurn 落库（会话 upsert + 两条消息）。
 * Python worker 不直连 MySQL，每轮对话结果经此回调落库。
 *
 * @author pulinsenz
 */
@SpringBootTest(properties = {
        "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef",
        "app.agent.token=test-agent-token"
})
@AutoConfigureMockMvc
class ChatCallbackControllerTest {

    private static final String TOKEN = "test-agent-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> createdSessionIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String sid : createdSessionIds) {
            jdbcTemplate.update("DELETE FROM chat_message WHERE sessionId = ?", sid);
            jdbcTemplate.update("DELETE FROM chat_session WHERE sessionId = ?", sid);
        }
        for (Long uid : createdUserIds) {
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", uid);
        }
        createdSessionIds.clear();
        createdUserIds.clear();
    }

    private long registerUser() throws Exception {
        String account = "test_" + System.currentTimeMillis();
        String pass = "pass12345";
        String reg = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass, "checkPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long uid = objectMapper.readTree(reg).get("data").asLong();
        createdUserIds.add(uid);
        return uid;
    }

    private String callSave(String sessionId, long userId, String query, String answer, String token) throws Exception {
        return mockMvc.perform(post("/internal/agent/chat-save")
                        .header("X-Agent-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "session_id", sessionId,
                                "user_id", String.valueOf(userId),
                                "query", query,
                                "answer", answer))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void chatSave_success_createsSessionAndMessages() throws Exception {
        long uid = registerUser();
        String sid = "cb_" + System.nanoTime();
        createdSessionIds.add(sid);

        String body = callSave(sid, uid, "你好", "你好呀", TOKEN);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());

        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_session WHERE sessionId = ?", Integer.class, sid);
        assertEquals(1, sessionCount);
        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE sessionId = ?", Integer.class, sid);
        assertEquals(2, msgCount);
        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM chat_session WHERE sessionId = ?", String.class, sid);
        assertEquals("你好", title, "标题取首条用户消息");
    }

    @Test
    void chatSave_success_updatesExistingSessionAndTitleStays() throws Exception {
        long uid = registerUser();
        String sid = "cb2_" + System.nanoTime();
        createdSessionIds.add(sid);

        callSave(sid, uid, "第一问", "答一", TOKEN);
        callSave(sid, uid, "第二问", "答二", TOKEN);

        Integer sessionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_session WHERE sessionId = ?", Integer.class, sid);
        assertEquals(1, sessionCount, "同一会话不应重复创建");
        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chat_message WHERE sessionId = ?", Integer.class, sid);
        assertEquals(4, msgCount, "两轮共 4 条消息");
        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM chat_session WHERE sessionId = ?", String.class, sid);
        assertEquals("第一问", title, "标题保持首条用户消息");
    }

    @Test
    void chatSave_missingToken_rejected() throws Exception {
        long uid = registerUser();
        String body = callSave("cb3_" + System.nanoTime(), uid, "hi", "hi", "");
        assertEquals(40101, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void chatSave_wrongToken_rejected() throws Exception {
        long uid = registerUser();
        String body = callSave("cb4_" + System.nanoTime(), uid, "hi", "hi", "wrong-token");
        assertEquals(40101, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void chatSave_blankSession_rejected() throws Exception {
        long uid = registerUser();
        String body = mockMvc.perform(post("/internal/agent/chat-save")
                        .header("X-Agent-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "session_id", "", "user_id", String.valueOf(uid),
                                "query", "hi", "answer", "hi"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, objectMapper.readTree(body).get("code").asInt());
    }
}
