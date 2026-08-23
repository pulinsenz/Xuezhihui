package com.agent.rag.controller;

import cn.hutool.core.io.FileUtil;
import com.agent.rag.config.JwtProperties;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库接口集成测试（MockMvc + 真实 MySQL/Redis）
 * <p>
 * 覆盖：创建→列表→multipart 上传→任务提交→文档列表→任务状态查询→删除 全链路；
 * 向量化走 Redis 消息队列（Python worker 消费），集成环境不启动 worker，仅验证任务已提交且状态可查；
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

        // multipart 上传：提交向量化任务，返回任务 id
        MockMultipartFile file = new MockMultipartFile("file", "课程简介.txt", "text/plain",
                "数据结构与算法是计算机核心课程".getBytes(StandardCharsets.UTF_8));
        String up = mockMvc.perform(multipart("/knowledge/{id}/upload", knowledgeId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode upNode = objectMapper.readTree(up);
        assertEquals(0, upNode.get("code").asInt());
        String taskId = upNode.get("data").asText();
        assertTrue(StringUtils.hasText(taskId), "上传应返回向量化任务 id");

        // 文档列表
        String docs = mockMvc.perform(get("/knowledge/{id}/docs", knowledgeId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, objectMapper.readTree(docs).get("data").size());

        // 任务状态可查（PENDING/PROCESSING/SUCCESS 由 Python worker 消费队列决定，此处仅验证接口可用）
        String task = mockMvc.perform(get("/task/{id}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode taskNode = objectMapper.readTree(task);
        assertEquals(0, taskNode.get("code").asInt());
        assertTrue(taskNode.get("data").has("status"), "任务状态应可查询");

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

    // ---------- 同文件去重（SHA-256）----------

    private String upload(String token, long knowledgeId, String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", content);
        return mockMvc.perform(multipart("/knowledge/{id}/upload", knowledgeId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode docs(String token, long knowledgeId) throws Exception {
        // 默认只看正常文档（deleted=0），与旧行为一致
        String resp = mockMvc.perform(get("/knowledge/{id}/docs", knowledgeId)
                        .param("deleted", "0")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("data");
    }

    private JsonNode docsWithDeleted(String token, long knowledgeId, Integer deleted) throws Exception {
        String resp = mockMvc.perform(get("/knowledge/{id}/docs", knowledgeId)
                        .param("deleted", String.valueOf(deleted))
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("data");
    }

    private String docIdByStatus(String token, long knowledgeId, String status) throws Exception {
        for (JsonNode d : docs(token, knowledgeId)) {
            if (status.equals(d.get("vectorStatus").asText())) {
                return d.get("id").asText();
            }
        }
        return null;
    }

    @Test
    void upload_duplicateContent_skipsVectorization() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "去重测试");

        byte[] content = "同一份内容，重复上传".getBytes(StandardCharsets.UTF_8);
        String first = upload(token, knowledgeId, "a.txt", content);
        assertTrue(StringUtils.hasText(objectMapper.readTree(first).get("data").asText()), "首次上传应返回任务 id");

        // 二次上传同内容（不同文件名）：创建 SKIPPED 记录，data 为 null 表示跳过入库
        String second = upload(token, knowledgeId, "b.txt", content);
        JsonNode secondNode = objectMapper.readTree(second);
        assertEquals(0, secondNode.get("code").asInt());
        assertTrue(secondNode.get("data").isNull(), "重复文件应返回 null 表示默认未入库");

        assertEquals(2, docs(token, knowledgeId).size(), "重复文件仍应创建文档记录");
        assertNotNull(docIdByStatus(token, knowledgeId, "SKIPPED"), "应存在 SKIPPED 记录");
    }

    @Test
    void upload_sameNameDifferentContent_allowed() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "去重测试2");

        String first = upload(token, knowledgeId, "note.txt", "内容A".getBytes(StandardCharsets.UTF_8));
        String second = upload(token, knowledgeId, "note.txt", "内容B不一样".getBytes(StandardCharsets.UTF_8));
        assertTrue(StringUtils.hasText(objectMapper.readTree(first).get("data").asText()));
        assertTrue(StringUtils.hasText(objectMapper.readTree(second).get("data").asText()), "同名不同内容应正常入库");
        assertEquals(2, docs(token, knowledgeId).size());
    }

    @Test
    void upload_afterDelete_reUploadAllowed() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "去重测试3");
        byte[] content = "删除后可重传".getBytes(StandardCharsets.UTF_8);
        upload(token, knowledgeId, "a.txt", content);

        // 逻辑删除旧文档（已删除不参与判重）
        jdbcTemplate.update("UPDATE knowledge_doc SET isDelete = 1 WHERE knowledgeId = ?", knowledgeId);

        String second = upload(token, knowledgeId, "b.txt", content);
        assertTrue(StringUtils.hasText(objectMapper.readTree(second).get("data").asText()), "删除旧文档后重传同内容应正常入库");
    }

    @Test
    void revectorize_skippedDoc_forcesVectorize() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "去重测试4");
        byte[] content = "强制入库内容".getBytes(StandardCharsets.UTF_8);
        upload(token, knowledgeId, "a.txt", content);
        upload(token, knowledgeId, "b.txt", content); // 重复 → SKIPPED

        String skippedDocId = docIdByStatus(token, knowledgeId, "SKIPPED");
        assertNotNull(skippedDocId);

        String resp = mockMvc.perform(post("/knowledge/{id}/docs/{docId}/revectorize", knowledgeId, skippedDocId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(resp);
        assertEquals(0, node.get("code").asInt());
        assertTrue(StringUtils.hasText(node.get("data").asText()), "强制入库应返回任务 id");
    }

    @Test
    void revectorize_successDoc_rejected() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "去重测试5");
        upload(token, knowledgeId, "a.txt", "已入库内容".getBytes(StandardCharsets.UTF_8));

        // 模拟已入库
        jdbcTemplate.update("UPDATE knowledge_doc SET vectorStatus = 'SUCCESS' WHERE knowledgeId = ?", knowledgeId);
        String docId = docs(token, knowledgeId).get(0).get("id").asText();

        String resp = mockMvc.perform(post("/knowledge/{id}/docs/{docId}/revectorize", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, objectMapper.readTree(resp).get("code").asInt(), "已入库文档不应再次入库");
    }

    // ---------- 默认入库设置 + 移除入库 ----------

    private long currentUserId(String token) throws Exception {
        String resp = mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("data").get("id").asLong();
    }

    @Test
    void upload_defaultNotVectorize_createsRemovedDoc() throws Exception {
        String token = registerAndLogin();
        long uid = currentUserId(token);
        // 关闭"默认入库"
        jdbcTemplate.update("UPDATE user SET defaultVectorize = 0 WHERE id = ?", uid);
        long knowledgeId = createKnowledge(token, "设置测试");

        String up = upload(token, knowledgeId, "a.txt", "普通内容".getBytes(StandardCharsets.UTF_8));
        JsonNode node = objectMapper.readTree(up);
        assertEquals(0, node.get("code").asInt());
        assertTrue(node.get("data").isNull(), "默认不入库时应返回 null 表示跳过向量化");
        assertNotNull(docIdByStatus(token, knowledgeId, "REMOVED"), "应创建未入库(REMOVED)记录");
    }

    @Test
    void removeVector_success_marksRemoved() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "移除测试");
        upload(token, knowledgeId, "a.txt", "内容".getBytes(StandardCharsets.UTF_8));
        // 模拟已入库
        jdbcTemplate.update("UPDATE knowledge_doc SET vectorStatus = 'SUCCESS' WHERE knowledgeId = ?", knowledgeId);
        String docId = docs(token, knowledgeId).get(0).get("id").asText();

        String resp = mockMvc.perform(post("/knowledge/{id}/docs/{docId}/remove-vector", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(resp).get("code").asInt());
        // 直接查库断言（MockMvc 读连接偶发旧快照，绕过它验证真实落库结果）
        String status = jdbcTemplate.queryForObject(
                "SELECT vectorStatus FROM knowledge_doc WHERE id = ?", String.class, Long.valueOf(docId));
        assertEquals("REMOVED", status, "移除后状态应为未入库");
        assertEquals(1, docs(token, knowledgeId).size(), "文档记录应保留");
    }

    @Test
    void removeVector_notVectorized_rejected() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "移除测试2");
        upload(token, knowledgeId, "a.txt", "内容".getBytes(StandardCharsets.UTF_8)); // PENDING
        String docId = docs(token, knowledgeId).get(0).get("id").asText();

        String resp = mockMvc.perform(post("/knowledge/{id}/docs/{docId}/remove-vector", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, objectMapper.readTree(resp).get("code").asInt(), "未入库文档不应可移除");
    }

    // ---------- 单文档删除 + 批量操作 ----------

    private List<String> allDocIds(String token, long knowledgeId) throws Exception {
        List<String> ids = new ArrayList<>();
        for (JsonNode d : docs(token, knowledgeId)) {
            ids.add(d.get("id").asText());
        }
        return ids;
    }

    @Test
    void deleteDoc_single_deletesRow() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "单删测试");
        upload(token, knowledgeId, "a.txt", "内容A".getBytes(StandardCharsets.UTF_8));
        upload(token, knowledgeId, "b.txt", "内容B".getBytes(StandardCharsets.UTF_8));
        String docId = docs(token, knowledgeId).get(0).get("id").asText();

        String resp = mockMvc.perform(delete("/knowledge/{id}/docs/{docId}", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(resp).get("code").asInt());
        assertEquals(1, docs(token, knowledgeId).size(), "删除后只剩另一条");
    }

    @Test
    void batchRemoveVector_marksSelectedRemoved() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "批量移除");
        upload(token, knowledgeId, "a.txt", "内容A".getBytes(StandardCharsets.UTF_8));
        upload(token, knowledgeId, "b.txt", "内容B".getBytes(StandardCharsets.UTF_8));
        // 模拟两条都已入库
        jdbcTemplate.update("UPDATE knowledge_doc SET vectorStatus = 'SUCCESS' WHERE knowledgeId = ?", knowledgeId);

        String resp = mockMvc.perform(post("/knowledge/{id}/docs/batch-remove-vector", knowledgeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("docIds", allDocIds(token, knowledgeId)))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(2, objectMapper.readTree(resp).get("data").asInt(), "应移除 2 个文档的入库");
        assertEquals(2, docs(token, knowledgeId).size(), "文档记录保留");
        assertNotNull(docIdByStatus(token, knowledgeId, "REMOVED"), "应存在未入库记录");
    }

    @Test
    void batchDeleteDocs_deletesSelectedWithVectors() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "批量删除");
        upload(token, knowledgeId, "a.txt", "内容A".getBytes(StandardCharsets.UTF_8));
        upload(token, knowledgeId, "b.txt", "内容B".getBytes(StandardCharsets.UTF_8));

        String resp = mockMvc.perform(post("/knowledge/{id}/docs/batch-delete", knowledgeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("docIds", allDocIds(token, knowledgeId)))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(2, objectMapper.readTree(resp).get("data").asInt(), "应删除 2 个文档");
        assertEquals(0, docs(token, knowledgeId).size(), "批量删除后列表为空");
    }

    // ---------- 删除来源（用户删可自恢复 / 管理员删不可恢复且禁止上传）----------

    private String deleteSourceOf(String token, long knowledgeId, String docId) {
        return jdbcTemplate.queryForObject("SELECT deleteSource FROM knowledge_doc WHERE id = ?", String.class, Long.valueOf(docId));
    }

    @Test
    void deleteDoc_marksUserSource() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "删除来源测试");
        upload(token, knowledgeId, "a.txt", "内容".getBytes(StandardCharsets.UTF_8));
        String docId = docs(token, knowledgeId).get(0).get("id").asText();

        mockMvc.perform(delete("/knowledge/{id}/docs/{docId}", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        assertEquals("user", deleteSourceOf(token, knowledgeId, docId), "用户删除应记录来源为 user");
        assertEquals(0, docs(token, knowledgeId).size(), "正常列表不再显示已删除文档");
    }

    @Test
    void restoreDoc_userDeleted_success() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "恢复测试");
        upload(token, knowledgeId, "a.txt", "内容".getBytes(StandardCharsets.UTF_8));
        String docId = docs(token, knowledgeId).get(0).get("id").asText();

        mockMvc.perform(delete("/knowledge/{id}/docs/{docId}", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        String resp = mockMvc.perform(post("/knowledge/{id}/docs/{docId}/restore", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(resp);
        assertEquals(0, node.get("code").asInt());
        assertTrue(StringUtils.hasText(node.get("data").asText()), "恢复应返回重新入库任务 id");
        assertEquals(1, docs(token, knowledgeId).size(), "恢复后回到正常列表");
        assertNull(deleteSourceOf(token, knowledgeId, docId), "恢复后删除来源应清空");
    }

    @Test
    void restoreDoc_adminDeleted_rejected() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "恢复测试2");
        upload(token, knowledgeId, "a.txt", "内容".getBytes(StandardCharsets.UTF_8));
        String docId = docs(token, knowledgeId).get(0).get("id").asText();
        // 模拟管理员删除（来源=admin）
        jdbcTemplate.update("UPDATE knowledge_doc SET isDelete = 1, deleteSource = 'admin' WHERE id = ?", Long.valueOf(docId));

        String resp = mockMvc.perform(post("/knowledge/{id}/docs/{docId}/restore", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, objectMapper.readTree(resp).get("code").asInt(), "管理员删除的文件用户不可恢复");
    }

    @Test
    void upload_afterAdminDelete_forbidden() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "禁止上传");
        byte[] content = "管理员删过的文件".getBytes(StandardCharsets.UTF_8);
        upload(token, knowledgeId, "a.txt", content);
        // 模拟管理员删除
        jdbcTemplate.update("UPDATE knowledge_doc SET isDelete = 1, deleteSource = 'admin' WHERE knowledgeId = ?", knowledgeId);

        String resp = upload(token, knowledgeId, "b.txt", content);
        assertEquals(40000, objectMapper.readTree(resp).get("code").asInt(), "管理员删除的文件禁止再上传");
    }

    @Test
    void upload_afterUserDelete_allowed() throws Exception {
        String token = registerAndLogin();
        long knowledgeId = createKnowledge(token, "重传测试");
        byte[] content = "用户删过可重传".getBytes(StandardCharsets.UTF_8);
        upload(token, knowledgeId, "a.txt", content);
        String docId = docs(token, knowledgeId).get(0).get("id").asText();
        // 用户删除（来源=user）
        mockMvc.perform(delete("/knowledge/{id}/docs/{docId}", knowledgeId, docId)
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        String resp = upload(token, knowledgeId, "b.txt", content);
        JsonNode node = objectMapper.readTree(resp);
        assertEquals(0, node.get("code").asInt(), "用户删除的文件应允许重传");
        assertTrue(StringUtils.hasText(node.get("data").asText()), "重传应正常入库返回任务 id");
    }
}
