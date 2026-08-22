package com.agent.rag.controller;

import com.agent.rag.common.VectorStatus;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 工具 Agent 回调内部接口测试（MockMvc + 真实 MySQL/Redis）
 * <p>
 * 覆盖：X-Agent-Token 鉴权（缺失/错误拒绝）、统计准确性、无数据零值、跨用户数据隔离。
 * 直接注入 mapper 造数，不依赖知识库上传链路（避免异步向量化线程）。
 *
 * @author pulinsenz
 */
@SpringBootTest(properties = {
        "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef",
        "app.agent.token=test-agent-token"
})
@AutoConfigureMockMvc
class AgentDataControllerTest {

    private static final String TOKEN = "test-agent-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private KnowledgeMapper knowledgeMapper;
    @Autowired
    private KnowledgeDocMapper knowledgeDocMapper;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdKnowledgeIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long kid : createdKnowledgeIds) {
            jdbcTemplate.update("DELETE FROM knowledge_doc WHERE knowledgeId = ?", kid);
            jdbcTemplate.update("DELETE FROM knowledge WHERE id = ?", kid);
        }
        for (Long uid : createdUserIds) {
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", uid);
        }
        createdKnowledgeIds.clear();
        createdUserIds.clear();
    }

    /** 注册用户（internal 接口无需登录，仅需存在该 userId 的数据） */
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

    private long insertKnowledge(long userId, String name) {
        Knowledge k = new Knowledge();
        k.setUserId(userId);
        k.setName(name);
        k.setCreateTime(LocalDateTime.now());
        knowledgeMapper.insert(k);
        createdKnowledgeIds.add(k.getId());
        return k.getId();
    }

    private void insertDoc(long knowledgeId, VectorStatus status) {
        KnowledgeDoc d = new KnowledgeDoc();
        d.setKnowledgeId(knowledgeId);
        d.setName("doc.txt");
        d.setFileUrl("/data/test/doc.txt");
        d.setFileSize(100L);
        d.setFileType("txt");
        d.setVectorStatus(status.name());
        d.setCreateTime(LocalDateTime.now());
        knowledgeDocMapper.insert(d);
    }

    private JsonNode fetchStats(long userId, String token) throws Exception {
        String body = mockMvc.perform(get("/internal/agent/user-stats")
                        .param("user_id", String.valueOf(userId))
                        .header("X-Agent-Token", token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    @Test
    void userStats_missingToken_rejected() throws Exception {
        String body = mockMvc.perform(get("/internal/agent/user-stats").param("user_id", "1"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, objectMapper.readTree(body).get("code").asInt(), "缺失内部 token 应拒绝");
    }

    @Test
    void userStats_wrongToken_rejected() throws Exception {
        String body = mockMvc.perform(get("/internal/agent/user-stats")
                        .param("user_id", "1")
                        .header("X-Agent-Token", "wrong-token"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, objectMapper.readTree(body).get("code").asInt(), "错误内部 token 应拒绝");
    }

    @Test
    void userStats_returnsAccurateCounts() throws Exception {
        long uid = registerUser();
        long kb1 = insertKnowledge(uid, "计算机课程");
        long kb2 = insertKnowledge(uid, "校园生活");
        insertDoc(kb1, VectorStatus.SUCCESS);
        insertDoc(kb1, VectorStatus.SUCCESS);
        insertDoc(kb2, VectorStatus.PENDING);

        JsonNode root = fetchStats(uid, TOKEN);
        assertEquals(0, root.get("code").asInt());
        JsonNode data = root.get("data");
        assertEquals(String.valueOf(uid), data.get("userId").asText());
        assertEquals("2", data.get("knowledge_count").asText());
        assertEquals("3", data.get("doc_count").asText());
        assertEquals("2", data.get("vector_success").asText());
        assertEquals("1", data.get("vector_pending").asText());
        assertEquals("0", data.get("vector_failed").asText());
        assertEquals(true, data.has("last_upload_time"), "最近上传时间应有值");
    }

    @Test
    void userStats_noData_returnsZeros() throws Exception {
        long uid = registerUser();
        JsonNode data = fetchStats(uid, TOKEN).get("data");
        assertEquals("0", data.get("knowledge_count").asText());
        assertEquals("0", data.get("doc_count").asText());
        assertEquals("0", data.get("vector_failed").asText());
    }

    @Test
    void userStats_isolatesBetweenUsers() throws Exception {
        long uidA = registerUser();
        long uidB = registerUser();
        insertDoc(insertKnowledge(uidA, "A 的库"), VectorStatus.SUCCESS);

        // 查询 A 只返回 A 的数据
        JsonNode dataA = fetchStats(uidA, TOKEN).get("data");
        assertEquals("1", dataA.get("knowledge_count").asText());
        assertEquals("1", dataA.get("doc_count").asText());
        // 查询 B 不应越权看到 A 的数据
        JsonNode dataB = fetchStats(uidB, TOKEN).get("data");
        assertEquals("0", dataB.get("knowledge_count").asText());
        assertEquals("0", dataB.get("doc_count").asText());
    }
}
