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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员接口集成测试（MockMvc + 真实 MySQL/Redis）
 * <p>
 * 覆盖：admin 用户管理全链路（列表/改角色/删除+强制下线）；
 * 普通 user 访问管理接口被拒；未登录被拦截。测试自清理。
 *
 * @author pulinsenz
 */
@SpringBootTest(properties = "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef")
@AutoConfigureMockMvc
class AdminControllerTest {

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
    // 本测试封禁的哈希（管理员删除级联测试产生），tearDown 清理黑名单表
    private final Set<String> createdHashes = new HashSet<>();

    @AfterEach
    void tearDown() {
        createdTokens.forEach(token ->
                stringRedisTemplate.delete(jwtProperties.getRedisPrefix() + token));
        createdTokens.clear();
        for (Long uid : createdUserIds) {
            jdbcTemplate.update("DELETE kd FROM knowledge_doc kd JOIN knowledge k ON kd.knowledgeId = k.id WHERE k.userId = ?", uid);
            jdbcTemplate.update("DELETE FROM knowledge WHERE userId = ?", uid);
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", uid);
        }
        // 清理黑名单表本测试产生的封禁哈希
        for (String hash : createdHashes) {
            jdbcTemplate.update("DELETE FROM forbidden_file_hash WHERE fileHash = ?", hash);
        }
        createdHashes.clear();
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

    private long registerUser(String account, String pass) throws Exception {
        String reg = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass, "checkPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long uid = objectMapper.readTree(reg).get("data").asLong();
        createdUserIds.add(uid);
        return uid;
    }

    private String login(String account, String pass) throws Exception {
        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("userAccount", account, "userPassword", pass))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = objectMapper.readTree(login).get("data").get("token").asText();
        createdTokens.add(token);
        return token;
    }

    /** 注册一个普通用户并提权为 admin，返回登录 token */
    private String createAdminAndLogin() throws Exception {
        String account = "admin_" + System.currentTimeMillis();
        String pass = "pass12345";
        long uid = registerUser(account, pass);
        jdbcTemplate.update("UPDATE user SET userRole='admin' WHERE id=?", uid);
        return login(account, pass);
    }

    private String createNormalUserAndLogin() throws Exception {
        String account = "test_" + System.currentTimeMillis();
        String pass = "pass12345";
        registerUser(account, pass);
        return login(account, pass);
    }

    private long createKnowledge(String token, String name) throws Exception {
        String resp = mockMvc.perform(post("/knowledge/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("data").asLong();
    }

    /** 上传文档，返回原始响应字符串（调用方自行解析 code/data） */
    private String uploadResp(String token, long knowledgeId, String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", content);
        return mockMvc.perform(multipart("/knowledge/{id}/upload", knowledgeId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** 取知识库第一条文档的 id */
    private String firstDocId(String token, long knowledgeId) throws Exception {
        String resp = mockMvc.perform(get("/knowledge/{id}/docs", knowledgeId)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("data").get(0).get("id").asText();
    }

    /** 计算文件内容 SHA-256（与后端 DigestUtil.sha256Hex 一致） */
    private String sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    void admin_canManageUsers() throws Exception {
        String adminToken = createAdminAndLogin();

        // 用户列表（至少含自己）
        String list = mockMvc.perform(get("/admin/user/list").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode listNode = objectMapper.readTree(list);
        assertEquals(0, listNode.get("code").asInt());
        assertTrue(listNode.get("data").get("records").size() >= 1);

        // 改角色：普通用户 → admin，DB 实时生效
        String targetAccount = "test_" + System.currentTimeMillis();
        long targetId = registerUser(targetAccount, "pass12345");
        String roleResp = mockMvc.perform(put("/admin/user/{id}/role", targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userRole\":\"admin\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(roleResp).get("code").asInt());
        assertEquals("admin",
                jdbcTemplate.queryForObject("SELECT userRole FROM user WHERE id=?", String.class, targetId));

        // 删除普通用户：其 token 立即失效
        String victimAccount = "test_" + System.currentTimeMillis();
        long victimId = registerUser(victimAccount, "pass12345");
        String victimToken = login(victimAccount, "pass12345");
        // 删除前能访问 /me
        String meBefore = mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + victimToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(meBefore).get("code").asInt());

        String delResp = mockMvc.perform(delete("/admin/user/{id}", victimId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(delResp).get("code").asInt());
        // 删除后 token 访问 /me → 白名单已清、用户已删 → 401
        String meAfter = mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + victimToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(meAfter).get("code").asInt());
    }

    @Test
    void normalUser_cannotAccessAdminApi() throws Exception {
        String userToken = createNormalUserAndLogin();
        String resp = mockMvc.perform(get("/admin/user/list").header("Authorization", "Bearer " + userToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, objectMapper.readTree(resp).get("code").asInt());
    }

    @Test
    void admin_requiresLogin() throws Exception {
        String resp = mockMvc.perform(get("/admin/user/list"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40100, objectMapper.readTree(resp).get("code").asInt());
    }

    @Test
    void admin_canSeeAndRestoreDeletedUsers() throws Exception {
        String adminToken = createAdminAndLogin();
        String victimAccount = "test_" + System.currentTimeMillis();
        long victimId = registerUser(victimAccount, "pass12345");
        login(victimAccount, "pass12345");

        // 删除用户
        mockMvc.perform(delete("/admin/user/{id}", victimId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn();

        // 正常列表（deleted=0）看不到已删除用户
        String normalList = mockMvc.perform(get("/admin/user/list")
                        .param("deleted", "0")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(objectMapper.readTree(normalList).get("data").get("records").toString().contains(victimAccount));

        // deleted=1 列表能看到已删除用户
        String deletedList = mockMvc.perform(get("/admin/user/list")
                        .param("deleted", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(objectMapper.readTree(deletedList).get("data").get("records").toString().contains(victimAccount));

        // 恢复用户
        String restore = mockMvc.perform(put("/admin/user/{id}/restore", victimId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(restore).get("code").asInt());
        assertEquals("0",
                jdbcTemplate.queryForObject("SELECT isDelete FROM user WHERE id=?", String.class, victimId));

        // 恢复后 deleted=1 列表不再包含
        String deletedList2 = mockMvc.perform(get("/admin/user/list")
                        .param("deleted", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(objectMapper.readTree(deletedList2).get("data").get("records").toString().contains(victimAccount));
    }

    // ---------- 管理员删除文档：记录哈希 + 级联删除相同文件 + 禁止上传/恢复 ----------

    @Test
    void adminDeleteDoc_cascadesAcrossUsersAndBansHash() throws Exception {
        String adminToken = createAdminAndLogin();
        // 用户 A、B 各自知识库上传同一份内容
        String tokenA = createNormalUserAndLogin();
        long kA = createKnowledge(tokenA, "A 的知识库");
        byte[] content = "管理员要封禁的敏感文件内容".getBytes(StandardCharsets.UTF_8);
        String uploadA = uploadResp(tokenA, kA, "a.txt", content);
        assertEquals(0, objectMapper.readTree(uploadA).get("code").asInt());
        String docIdA = firstDocId(tokenA, kA);
        String hash = sha256(content);
        createdHashes.add(hash);

        String tokenB = createNormalUserAndLogin();
        long kB = createKnowledge(tokenB, "B 的知识库");
        String uploadB = uploadResp(tokenB, kB, "b.txt", content);
        assertEquals(0, objectMapper.readTree(uploadB).get("code").asInt());

        // 管理员删除 A 的文档
        String delResp = mockMvc.perform(delete("/admin/knowledge/{id}/docs/{docId}", kA, docIdA)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, objectMapper.readTree(delResp).get("code").asInt());

        // ① 级联：A、B 的同内容文档均标记为 admin 删除
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT isDelete, deleteSource FROM knowledge_doc WHERE fileHash = ?", hash);
        assertEquals(2, rows.size(), "同内容文档应全部被级联删除");
        rows.forEach(r -> {
            assertEquals(1, ((Number) r.get("isDelete")).intValue());
            assertEquals("admin", r.get("deleteSource"));
        });

        // ② 记录哈希：黑名单表存在该哈希
        Long bannedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM forbidden_file_hash WHERE fileHash = ?", Long.class, hash);
        assertEquals(1L, bannedCount, "管理员删除后应记录封禁哈希");

        // ③ A/B 重传同内容均被拒
        JsonNode reA = objectMapper.readTree(uploadResp(tokenA, kA, "a2.txt", content));
        assertEquals(40000, reA.get("code").asInt(), "被封禁哈希禁止再次上传");
        JsonNode reB = objectMapper.readTree(uploadResp(tokenB, kB, "b2.txt", content));
        assertEquals(40000, reB.get("code").asInt(), "被封禁哈希跨用户也禁止上传");

        // ④ 用户 / 管理员恢复均被拒
        String restoreUser = mockMvc.perform(post("/knowledge/{id}/docs/{docId}/restore", kA, docIdA)
                        .header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, objectMapper.readTree(restoreUser).get("code").asInt(), "用户不可恢复被封禁文件");
        String restoreAdmin = mockMvc.perform(put("/admin/knowledge/{id}/docs/{docId}/restore", kA, docIdA)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, objectMapper.readTree(restoreAdmin).get("code").asInt(), "管理员也不可恢复被封禁文件");

        // ⑤ 管理员恢复整个知识库时，被封禁哈希的文档保持删除
        mockMvc.perform(delete("/admin/knowledge/{id}", kA)
                        .header("Authorization", "Bearer " + adminToken)).andReturn();
        mockMvc.perform(put("/admin/knowledge/{id}/restore", kA)
                        .header("Authorization", "Bearer " + adminToken)).andReturn();
        List<Map<String, Object>> afterRestore = jdbcTemplate.queryForList(
                "SELECT isDelete FROM knowledge_doc WHERE id = ?", Long.valueOf(docIdA));
        assertEquals(1, ((Number) afterRestore.get(0).get("isDelete")).intValue(),
                "恢复知识库后被封禁文档应保持删除");
    }
}
