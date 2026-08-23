package com.agent.rag.controller;

import com.agent.rag.common.VectorStatus;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.mapper.KnowledgeDocMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Python worker 向量化完成回调接口测试（MockMvc + 真实 MySQL）
 * <p>
 * 覆盖：X-Agent-Token 鉴权（缺失/错误拒绝）、SUCCESS/FAILED 回写文档状态。
 * 直接注入 mapper 造 PENDING 文档，模拟 worker 回调 Java 更新 MySQL（Python 不直连 MySQL 的信任边界）。
 *
 * @author pulinsenz
 */
@SpringBootTest(properties = {
        "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef",
        "app.agent.token=test-agent-token"
})
@AutoConfigureMockMvc
class VectorCallbackControllerTest {

    private static final String TOKEN = "test-agent-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private KnowledgeDocMapper knowledgeDocMapper;

    private final List<Long> createdDocIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long docId : createdDocIds) {
            jdbcTemplate.update("DELETE FROM knowledge_doc WHERE id = ?", docId);
        }
        for (Long uid : createdUserIds) {
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", uid);
        }
        createdDocIds.clear();
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

    /** 造一条 PENDING 文档（模拟上传后待向量化状态） */
    private long insertPendingDoc(long userId) {
        KnowledgeDoc d = new KnowledgeDoc();
        d.setKnowledgeId(9999L);
        d.setName("course.txt");
        d.setFileUrl("/data/test/course.txt");
        d.setFileSize(100L);
        d.setFileType("txt");
        d.setVectorStatus(VectorStatus.PENDING.name());
        d.setCreateTime(LocalDateTime.now());
        knowledgeDocMapper.insert(d);
        createdDocIds.add(d.getId());
        return d.getId();
    }

    private String callCallback(String docId, String status, String token) throws Exception {
        return mockMvc.perform(post("/internal/agent/vector-callback")
                        .header("X-Agent-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("doc_id", docId, "status", status))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void callback_success_updatesDocToSuccess() throws Exception {
        registerUser();
        long docId = insertPendingDoc(1L);

        String body = callCallback(String.valueOf(docId), VectorStatus.SUCCESS.name(), TOKEN);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());

        assertEquals(VectorStatus.SUCCESS.name(), knowledgeDocMapper.selectById(docId).getVectorStatus());
    }

    @Test
    void callback_failure_recordsErrorMsg() throws Exception {
        registerUser();
        long docId = insertPendingDoc(1L);

        String body = callCallback(String.valueOf(docId), VectorStatus.FAILED.name(), TOKEN);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());

        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        assertEquals(VectorStatus.FAILED.name(), doc.getVectorStatus());
    }

    @Test
    void callback_missingToken_rejected() throws Exception {
        registerUser();
        long docId = insertPendingDoc(1L);
        String body = callCallback(String.valueOf(docId), VectorStatus.SUCCESS.name(), "");
        assertEquals(40101, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void callback_wrongToken_rejected() throws Exception {
        registerUser();
        long docId = insertPendingDoc(1L);
        String body = callCallback(String.valueOf(docId), VectorStatus.SUCCESS.name(), "wrong-token");
        assertEquals(40101, objectMapper.readTree(body).get("code").asInt());
    }
}
