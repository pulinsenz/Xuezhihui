package com.agent.rag.controller;

import cn.hutool.core.io.FileUtil;
import com.agent.rag.common.VectorStatus;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库接口集成测试（MockMvc + 真实 MySQL/Redis）
 * <p>
 * 覆盖：创建→列表→multipart 上传→文档列表→删除 全链路；
 * Python Agent 未启动时向量化优雅降级为 FAILED；
 * 未登录/跨用户访问被拦截。测试自清理数据库与上传文件。
 *
 * @author pulinsenz
 */
@SpringBootTest(properties = "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef")
@AutoConfigureMockMvc
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private KnowledgeDocMapper knowledgeDocMapper;
    @Autowired
    private JwtProperties jwtProperties;
    @Value("${app.file.storage.local-path}")
    private String storagePath;

    private final List<String> createdTokens = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // 清理 Redis 白名单
        createdTokens.forEach(token ->
                stringRedisTemplate.delete(jwtProperties.getRedisPrefix() + token));
        createdTokens.clear();
        // 清理测试数据（仅本测试产生的用户/知识库/文档）
        for (Long uid : createdUserIds) {
            jdbcTemplate.update("DELETE kd FROM knowledge_doc kd JOIN knowledge k ON kd.knowledgeId = k.id WHERE k.userId = ?", uid);
            jdbcTemplate.update("DELETE FROM knowledge WHERE userId = ?", uid);
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", uid);
        }
        // 清理测试上传的本地文件
        for (Long uid : createdUserIds) {
            File base = new File(storagePath);
            File[] months = base.listFiles();
            if (months != null) {
                for (File month : months) {
                    File userDir = new File(month, String.valueOf(uid));
                    if (userDir.exists()) {
                        FileUtil.del(userDir);
                    }
                }
            }
        }
        createdUserIds.clear();
    }

    /** 注册 + 登录，返回 token */
    private String registerAndLogin() throws Exception {
        String account = "test_" + System.currentTimeMillis();
        String pass = "pass12345";
        String reg = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass, "checkPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long uid = objectMapper.readTree(reg).get("data").asLong();
        createdUserIds.add(uid);

        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = objectMapper.readTree(login).get("data").get("token").asText();
        createdTokens.add(token);
        return token;
    }

    private long createKnowledge(String token, String name) throws Exception {
        String resp = mockMvc.perform(post("/knowledge/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("data").asLong();
    }

    /** 等待向量化状态不再是 PENDING */
    private KnowledgeDoc waitForVectorStatus(Long docId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        KnowledgeDoc doc;
        do {
            doc = knowledgeDocMapper.selectById(docId);
            if (doc != null && !VectorStatus.PENDING.name().equals(doc.getVectorStatus())) {
                return doc;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        return doc;
    }

    @Test
    void knowledge_fullFlow() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "计算机课程");
        assertTrue(knowledgeId > 0);

        // 列表
        String list = mockMvc.perform(get("/knowledge/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(list).get("code").asInt());

        // multipart 上传
        MockMultipartFile file = new MockMultipartFile("file", "课程简介.txt", "text/plain",
                "数据结构与算法是计算机核心课程".getBytes(StandardCharsets.UTF_8));
        String up = mockMvc.perform(multipart("/knowledge/{id}/upload", knowledgeId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode upNode = objectMapper.readTree(up);
        assertEquals(0, upNode.get("code").asInt());
        long docId = upNode.get("data").asLong();

        // 文档列表
        String docs = mockMvc.perform(get("/knowledge/{id}/docs", knowledgeId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, objectMapper.readTree(docs).get("data").size());

        // 等待异步向量化完成：Python 未启动 → 优雅降级为 FAILED
        KnowledgeDoc doc = waitForVectorStatus(docId, 8000);
        assertEquals(VectorStatus.FAILED.name(), doc.getVectorStatus());
        assertTrue(StringUtils.hasText(doc.getErrorMsg()), "应记录失败原因");

        // 删除知识库
        String del = mockMvc.perform(delete("/knowledge/{id}", knowledgeId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(del).get("code").asInt());
    }

    @Test
    void knowledge_requiresLogin() throws Exception {
        String resp = mockMvc.perform(get("/knowledge/list"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(resp).get("code").asInt());
    }

    @Test
    void knowledge_cannotAccessOthers() throws Exception {
        String tokenA = registerAndLogin();
        long knowledgeId = createKnowledge(tokenA, "A 的知识库");

        String tokenB = registerAndLogin();
        String resp = mockMvc.perform(get("/knowledge/{id}", knowledgeId)
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, objectMapper.readTree(resp).get("code").asInt(), "跨用户访问应无权限");
    }
}
