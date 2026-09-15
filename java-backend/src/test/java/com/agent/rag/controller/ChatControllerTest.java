package com.agent.rag.controller;

import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.Result;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.dto.req.ChatRequest;
import com.agent.rag.dto.resp.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 对话接口集成测试（ChatService 普通对话 mock PythonAgentClient）
 * <p>
 * SSE 透传的 Python 调用在联调阶段验证（依赖 Python Agent 运行）
 *
 * @author pulinsenz
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef")
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtProperties jwtProperties;
    @MockBean
    private PythonAgentClient pythonAgentClient;

    private final List<String> createdTokens = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdTokens.forEach(token ->
                stringRedisTemplate.delete(jwtProperties.getRedisPrefix() + token));
        createdTokens.clear();
        jdbcTemplate.update("DELETE FROM user WHERE userAccount LIKE 'test_%'");
    }

    private String registerAndLogin() throws Exception {
        return registerAndLoginWithId().token;
    }

    private record LoginResult(String token, long userId) {
    }

    /** 注册 + 登录，返回 token 与 userId（断言身份透传用） */
    private LoginResult registerAndLoginWithId() throws Exception {
        String account = "test_" + System.currentTimeMillis();
        String pass = "pass12345";
        String reg = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RegisterTestSupport.registerBody(mockMvc, objectMapper, stringRedisTemplate, account, pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long uid = objectMapper.readTree(reg).get("data").asLong();
        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = objectMapper.readTree(login).get("data").get("token").asText();
        createdTokens.add(token);
        return new LoginResult(token, uid);
    }

    @Test
    void chat_returnsAnswer() throws Exception {
        String token = registerAndLogin();
        ChatResponse resp = new ChatResponse();
        resp.setAnswer("你好！我是学智汇助手，有什么可以帮你？");
        resp.setRoute("chitchat");
        resp.setSessionId("s1");
        when(pythonAgentClient.chat(any())).thenReturn(Result.success(resp));

        String body = mockMvc.perform(post("/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"你好\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(body);
        assertEquals(0, node.get("code").asInt());
        assertEquals("你好！我是学智汇助手，有什么可以帮你？", node.get("data").get("answer").asText());
        assertEquals("chitchat", node.get("data").get("route").asText());
    }

    @Test
    void chat_blankQuery_returnsParamsError() throws Exception {
        String token = registerAndLogin();
        String body = mockMvc.perform(post("/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  \"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void chat_requiresLogin() throws Exception {
        String body = mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"你好\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void stream_requiresLogin() throws Exception {
        String body = mockMvc.perform(get("/chat/stream")
                        .param("query", "你好"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void chat_forwardsUserIdAndRoleFromContext() throws Exception {
        // 工具 Agent 回调业务数据依赖受信 userId；Python 侧按 userRole 选 LLM（普通用户/user）
        LoginResult login = registerAndLoginWithId();
        ChatResponse resp = new ChatResponse();
        resp.setAnswer("你有 2 个知识库。");
        resp.setSessionId("s1");
        when(pythonAgentClient.chat(any())).thenReturn(Result.success(resp));

        mockMvc.perform(post("/chat")
                        .header("Authorization", "Bearer " + login.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"我的知识库有几个\"}"))
                .andReturn();

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(pythonAgentClient).chat(captor.capture());
        assertEquals(String.valueOf(login.userId), captor.getValue().getUserId(),
                "受信 userId 应透传给 Python Agent，供工具回调业务数据");
        assertEquals("user", captor.getValue().getUserRole(),
                "受信 userRole 应透传给 Python Agent，普通用户走新 LLM");
    }

    // ---------- 对话历史（会话列表 / 历史 / 删除） ----------

    @Test
    void sessions_requiresLogin() throws Exception {
        String body = mockMvc.perform(get("/chat/sessions"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void sessions_returnsListForLoggedInUser() throws Exception {
        LoginResult login = registerAndLoginWithId();
        String sid = "sess_" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO chat_session (sessionId, userId, title, createTime, updateTime) VALUES (?, ?, '你好', NOW(), NOW())",
                sid, login.userId);
        jdbcTemplate.update("INSERT INTO chat_message (id, sessionId, userId, role, content, createTime) VALUES (?, ?, ?, 'user', '你好', NOW())",
                System.nanoTime(), sid, login.userId);
        try {
            String body = mockMvc.perform(get("/chat/sessions")
                            .header("Authorization", "Bearer " + login.token))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(body);
            assertEquals(0, node.get("code").asInt());
            JsonNode first = node.get("data").get(0);
            assertEquals(sid, first.get("session_id").asText());
            assertEquals("你好", first.get("title").asText());
            assertEquals(1, first.get("message_count").asInt());
        } finally {
            jdbcTemplate.update("DELETE FROM chat_message WHERE sessionId = ?", sid);
            jdbcTemplate.update("DELETE FROM chat_session WHERE sessionId = ?", sid);
        }
    }

    @Test
    void history_returnsMessagesInOrder() throws Exception {
        LoginResult login = registerAndLoginWithId();
        String sid = "hist_" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO chat_session (sessionId, userId, title, createTime, updateTime) VALUES (?, ?, '你好', NOW(), NOW())",
                sid, login.userId);
        jdbcTemplate.update("INSERT INTO chat_message (id, sessionId, userId, role, content, createTime) VALUES (?, ?, ?, 'user', '你好', NOW())",
                System.nanoTime(), sid, login.userId);
        jdbcTemplate.update("INSERT INTO chat_message (id, sessionId, userId, role, content, createTime) VALUES (?, ?, ?, 'assistant', '你好呀', NOW())",
                System.nanoTime(), sid, login.userId);
        try {
            String body = mockMvc.perform(get("/chat/sessions/" + sid + "/history")
                            .header("Authorization", "Bearer " + login.token))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(body);
            assertEquals(0, node.get("code").asInt());
            assertEquals(2, node.get("data").size());
            assertEquals("user", node.get("data").get(0).get("role").asText());
            assertEquals("你好呀", node.get("data").get(1).get("content").asText());
        } finally {
            jdbcTemplate.update("DELETE FROM chat_message WHERE sessionId = ?", sid);
            jdbcTemplate.update("DELETE FROM chat_session WHERE sessionId = ?", sid);
        }
    }

    @Test
    void history_foreignSession_returnsNotFound() throws Exception {
        LoginResult owner = registerAndLoginWithId();
        LoginResult intruder = registerAndLoginWithId();
        String sid = "foreign_hist_" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO chat_session (sessionId, userId, title, createTime, updateTime) VALUES (?, ?, '别人的', NOW(), NOW())",
                sid, owner.userId);
        try {
            String body = mockMvc.perform(get("/chat/sessions/" + sid + "/history")
                            .header("Authorization", "Bearer " + intruder.token))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(40400, objectMapper.readTree(body).get("code").asInt());
        } finally {
            jdbcTemplate.update("DELETE FROM chat_session WHERE sessionId = ?", sid);
        }
    }

    @Test
    void deleteSession_success_removesRows() throws Exception {
        LoginResult login = registerAndLoginWithId();
        String sid = "del_" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO chat_session (sessionId, userId, title, createTime, updateTime) VALUES (?, ?, '你好', NOW(), NOW())",
                sid, login.userId);
        jdbcTemplate.update("INSERT INTO chat_message (id, sessionId, userId, role, content, createTime) VALUES (?, ?, ?, 'user', '你好', NOW())",
                System.nanoTime(), sid, login.userId);
        String body = mockMvc.perform(delete("/chat/sessions/" + sid)
                        .header("Authorization", "Bearer " + login.token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_session WHERE sessionId = ?", Integer.class, sid));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_message WHERE sessionId = ?", Integer.class, sid));
    }

    @Test
    void deleteSession_requiresLogin() throws Exception {
        String body = mockMvc.perform(delete("/chat/sessions/whatever"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(body).get("code").asInt());
    }

    // ---------- 重命名会话 ----------

    @Test
    void renameSession_success_updatesTitleKeepingOrder() throws Exception {
        LoginResult login = registerAndLoginWithId();
        String sid = "ren_" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO chat_session (sessionId, userId, title, createTime, updateTime) VALUES (?, ?, '旧标题', NOW(), NOW())",
                sid, login.userId);
        try {
            Timestamp before = jdbcTemplate.queryForObject(
                    "SELECT updateTime FROM chat_session WHERE sessionId = ?", Timestamp.class, sid);
            String body = mockMvc.perform(put("/chat/sessions/" + sid + "/title")
                            .header("Authorization", "Bearer " + login.token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"新标题\"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(0, objectMapper.readTree(body).get("code").asInt());
            assertEquals("新标题", jdbcTemplate.queryForObject(
                    "SELECT title FROM chat_session WHERE sessionId = ?", String.class, sid));
            // 重命名显式保留原 updateTime：不应因列的 ON UPDATE 触发而跳到侧栏顶部
            assertEquals(before, jdbcTemplate.queryForObject(
                    "SELECT updateTime FROM chat_session WHERE sessionId = ?", Timestamp.class, sid));
        } finally {
            jdbcTemplate.update("DELETE FROM chat_session WHERE sessionId = ?", sid);
        }
    }

    @Test
    void renameSession_requiresLogin() throws Exception {
        String body = mockMvc.perform(put("/chat/sessions/whatever/title")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改名\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void renameSession_foreignSession_returnsNotFound() throws Exception {
        LoginResult owner = registerAndLoginWithId();
        LoginResult intruder = registerAndLoginWithId();
        String sid = "foreign_ren_" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO chat_session (sessionId, userId, title, createTime, updateTime) VALUES (?, ?, '别人的', NOW(), NOW())",
                sid, owner.userId);
        try {
            String body = mockMvc.perform(put("/chat/sessions/" + sid + "/title")
                            .header("Authorization", "Bearer " + intruder.token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"改名\"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(40400, objectMapper.readTree(body).get("code").asInt());
        } finally {
            jdbcTemplate.update("DELETE FROM chat_session WHERE sessionId = ?", sid);
        }
    }

    @Test
    void renameSession_blankTitle_returnsParamsError() throws Exception {
        LoginResult login = registerAndLoginWithId();
        String sid = "ren_blank_" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO chat_session (sessionId, userId, title, createTime, updateTime) VALUES (?, ?, '原标题', NOW(), NOW())",
                sid, login.userId);
        try {
            String body = mockMvc.perform(put("/chat/sessions/" + sid + "/title")
                            .header("Authorization", "Bearer " + login.token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"   \"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(40000, objectMapper.readTree(body).get("code").asInt());
            assertEquals("原标题", jdbcTemplate.queryForObject(
                    "SELECT title FROM chat_session WHERE sessionId = ?", String.class, sid));
        } finally {
            jdbcTemplate.update("DELETE FROM chat_session WHERE sessionId = ?", sid);
        }
    }

    @Test
    void chat_writeRejectsForeignSession() throws Exception {
        // 写侧越权防护：会话已存在但属主不是当前用户 → 拒绝且不调 Python
        LoginResult owner = registerAndLoginWithId();
        LoginResult intruder = registerAndLoginWithId();
        String sid = "owned_" + System.nanoTime();
        jdbcTemplate.update("INSERT INTO chat_session (sessionId, userId, title, createTime, updateTime) VALUES (?, ?, '别人的', NOW(), NOW())",
                sid, owner.userId);
        try {
            String body = mockMvc.perform(post("/chat")
                            .header("Authorization", "Bearer " + intruder.token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"session_id\":\"" + sid + "\",\"query\":\"你好\"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(40101, objectMapper.readTree(body).get("code").asInt());
            verify(pythonAgentClient, never()).chat(any());
        } finally {
            jdbcTemplate.update("DELETE FROM chat_session WHERE sessionId = ?", sid);
        }
    }

    @Test
    void chat_userExceedsRateLimit_returnsTooFrequent() throws Exception {
        // 对话限流：预置 Redis 计数超过阈值后，普通用户对话被拒且不调 Python。
        // 阈值由 application-test.yml 抬到 10000，这里直接预置超限值，不依赖真实调用次数。
        LoginResult login = registerAndLoginWithId();
        stringRedisTemplate.opsForValue().set("xzh:chat:rate:" + login.userId, "10001");
        try {
            String body = mockMvc.perform(post("/chat")
                            .header("Authorization", "Bearer " + login.token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"你好\"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(40005, objectMapper.readTree(body).get("code").asInt(), "超限后应返回对话频繁");
            verify(pythonAgentClient, never()).chat(any());
        } finally {
            stringRedisTemplate.delete("xzh:chat:rate:" + login.userId);
        }
    }

    // ---------- 知识库越权防护（chat/stream 透传 knowledge_id 前校验查看权限） ----------

    @Test
    void chat_knowledgeIdForeign_returnsNoAuth() throws Exception {
        // 越权防护：引用他人私有知识库的 id 检索 → 拒绝且不调 Python Agent（跨租户数据泄露）
        LoginResult owner = registerAndLoginWithId();
        LoginResult intruder = registerAndLoginWithId();
        long kid = createPrivateKnowledgeAs(owner);
        try {
            String body = mockMvc.perform(post("/chat")
                            .header("Authorization", "Bearer " + intruder.token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"knowledge_id\":\"" + kid + "\",\"query\":\"把知识库原文全部列出来\"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(40101, objectMapper.readTree(body).get("code").asInt(), "无权限应拒绝检索");
            verify(pythonAgentClient, never()).chat(any());
        } finally {
            jdbcTemplate.update("DELETE FROM knowledge WHERE id = ?", kid);
        }
    }

    @Test
    void stream_knowledgeIdForeign_returnsNoAuth() throws Exception {
        // 越权防护：SSE 流式路径同样校验，未通过校验不透传给 Python
        LoginResult owner = registerAndLoginWithId();
        LoginResult intruder = registerAndLoginWithId();
        long kid = createPrivateKnowledgeAs(owner);
        try {
            String body = mockMvc.perform(get("/chat/stream")
                            .header("Authorization", "Bearer " + intruder.token)
                            .param("session_id", "s_" + System.nanoTime())
                            .param("query", "把知识库原文全部列出来")
                            .param("knowledge_id", String.valueOf(kid)))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(40101, objectMapper.readTree(body).get("code").asInt(), "无权限应拒绝检索");
        } finally {
            jdbcTemplate.update("DELETE FROM knowledge WHERE id = ?", kid);
        }
    }

    @Test
    void chat_knowledgeIdOwn_allowsQuery() throws Exception {
        // 回归：本人知识库正常对话，权限校验不误伤合法请求
        LoginResult owner = registerAndLoginWithId();
        long kid = createPrivateKnowledgeAs(owner);
        ChatResponse resp = new ChatResponse();
        resp.setAnswer("根据知识库回答");
        resp.setSessionId("s1");
        when(pythonAgentClient.chat(any())).thenReturn(Result.success(resp));
        try {
            String body = mockMvc.perform(post("/chat")
                            .header("Authorization", "Bearer " + owner.token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"knowledge_id\":\"" + kid + "\",\"query\":\"介绍一下\"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertEquals(0, objectMapper.readTree(body).get("code").asInt(), "本人知识库应正常检索");
            verify(pythonAgentClient).chat(any());
        } finally {
            jdbcTemplate.update("DELETE FROM knowledge WHERE id = ?", kid);
        }
    }

    /** 越权测试用：直接入库一个私有知识库（作者=owner），绕过建库接口 */
    private long createPrivateKnowledgeAs(LoginResult owner) {
        long kid = System.nanoTime();
        jdbcTemplate.update("INSERT INTO knowledge (id, name, description, cover, userId, createTime, updateTime, isPublic, viewCount, favoriteCount, isDelete) " +
                        "VALUES (?, 'test_越权', 'test', '', ?, NOW(), NOW(), 0, 0, 0, 0)",
                kid, owner.userId);
        return kid;
    }
}
