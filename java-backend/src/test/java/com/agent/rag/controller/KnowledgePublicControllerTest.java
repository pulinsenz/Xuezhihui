package com.agent.rag.controller;

import com.agent.rag.config.JwtProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.PutObjectRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公开知识库模块集成测试（MockMvc + 真实 MySQL/Redis）
 * <p>
 * 覆盖：公开列表、收藏/取消收藏、复制、协作者授权、编辑信息权限、查看者只读、浏览量计数。
 * 向量化走 Redis 消息队列，集成环境不启动 Python worker，仅验证任务已提交可查。
 *
 * @author pulinsenz
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef",
        "cos.client.host=https://test.cos.myqcloud.com",
        "cos.client.secret-id=test-secret-id",
        "cos.client.secret-key=test-secret-key",
        "cos.client.region=ap-guangzhou",
        "cos.client.bucket=test-bucket"
})
@AutoConfigureMockMvc
class KnowledgePublicControllerTest {

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
    // 封面上传走 COS：Mock 掉 SDK 客户端，避免真实网络/密钥
    @MockBean
    private COSClient cosClient;

    private final List<String> createdTokens = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    // 内存版 COS：文档/封面上传走 COS 后，任务消息签名与下载代理都需要 COSClient 替身返回可控结果
    private final Map<String, byte[]> cosStore = new HashMap<>();

    private record LoginUser(String token, String account, long id) {
    }

    @BeforeEach
    void setUpCosMock() throws Exception {
        cosStore.clear();
        when(cosClient.putObject(any(PutObjectRequest.class))).thenAnswer(inv -> {
            PutObjectRequest req = inv.getArgument(0);
            cosStore.put(req.getKey(), StreamUtils.copyToByteArray(req.getInputStream()));
            return null;
        });
        when(cosClient.generatePresignedUrl(anyString(), anyString(), any(Date.class)))
                .thenAnswer(inv -> new URL("https://test.cos.myqcloud.com/" + inv.getArgument(1) + "?q-sign-algorithm=sha1"));
    }

    @AfterEach
    void tearDown() {
        createdTokens.forEach(token ->
                stringRedisTemplate.delete(jwtProperties.getRedisPrefix() + token));
        createdTokens.clear();
        for (Long uid : createdUserIds) {
            // 收藏/协作者关系双向清理（知识库属主方向 + 用户方向）
            jdbcTemplate.update("DELETE kf FROM knowledge_favorite kf JOIN knowledge k ON kf.knowledgeId = k.id WHERE k.userId = ?", uid);
            jdbcTemplate.update("DELETE FROM knowledge_favorite WHERE userId = ?", uid);
            jdbcTemplate.update("DELETE km FROM knowledge_member km JOIN knowledge k ON km.knowledgeId = k.id WHERE k.userId = ?", uid);
            jdbcTemplate.update("DELETE FROM knowledge_member WHERE userId = ?", uid);
            jdbcTemplate.update("DELETE kd FROM knowledge_doc kd JOIN knowledge k ON kd.knowledgeId = k.id WHERE k.userId = ?", uid);
            jdbcTemplate.update("DELETE FROM knowledge WHERE userId = ?", uid);
            jdbcTemplate.update("DELETE FROM user WHERE id = ?", uid);
        }
        // 清理测试上传的本地文件（文档目录 + 封面目录 cover/）
        for (Long uid : createdUserIds) {
            File base = new File(storagePath);
            File[] months = base.listFiles();
            if (months != null) {
                for (File month : months) {
                    File userDir = new File(month, String.valueOf(uid));
                    if (userDir.exists()) {
                        cn.hutool.core.io.FileUtil.del(userDir);
                    }
                }
            }
            File coverBase = new File(base, "cover");
            File[] coverMonths = coverBase.listFiles();
            if (coverMonths != null) {
                for (File month : coverMonths) {
                    File userDir = new File(month, String.valueOf(uid));
                    if (userDir.exists()) {
                        cn.hutool.core.io.FileUtil.del(userDir);
                    }
                }
            }
        }
        createdUserIds.clear();
    }

    /** 注册 + 登录，返回 token/账号/id */
    private LoginUser registerAndLogin() throws Exception {
        String account = "pub_" + System.currentTimeMillis();
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
        return new LoginUser(token, account, uid);
    }

    private long createKnowledge(String token, String name, int isPublic) throws Exception {
        String resp = mockMvc.perform(post("/knowledge/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "isPublic", isPublic))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(resp).get("data").asLong();
    }

    private String upload(String token, long knowledgeId, String filename, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", content);
        return mockMvc.perform(multipart("/knowledge/{id}/upload", knowledgeId)
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int code(String resp) throws Exception {
        return objectMapper.readTree(resp).get("code").asInt();
    }

    private JsonNode data(String resp) throws Exception {
        return objectMapper.readTree(resp).get("data");
    }

    // ---------- 封面上传 + 静态访问 ----------

    @Test
    void cover_upload_returnsServablePublicUrl() throws Exception {
        LoginUser a = registerAndLogin();
        MockMultipartFile img = new MockMultipartFile("file", "cover.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        String resp = mockMvc.perform(multipart("/knowledge/cover")
                        .file(img)
                        .header("Authorization", "Bearer " + a.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, code(resp), "封面上传应成功");
        String url = objectMapper.readTree(resp).get("data").asText();
        // 封面已改走 COS：返回对象存储直链；public-read ACL 保证浏览器 <img> 免鉴权加载（ACL 由 CosFileStorageServiceTest 单测验证）
        assertTrue(url.startsWith("https://test.cos.myqcloud.com/cover/"), "应返回 COS 直链，实际: " + url);
    }

    // ---------- 公开列表 ----------

    @Test
    void publicList_showsOthersPublicOnly() throws Exception {
        LoginUser a = registerAndLogin();
        long publicId = createKnowledge(a.token(), "A 公开库", 1);
        createKnowledge(a.token(), "A 私有库", 0);

        LoginUser b = registerAndLogin();
        String resp = mockMvc.perform(get("/knowledge/public/list").header("Authorization", "Bearer " + b.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode list = data(resp);
        assertEquals(0, code(resp));
        // B 的公开列表应含 A 的公开库，不含私有库
        JsonNode target = findById(list, publicId);
        assertNotNull(target, "B 应能看到 A 的公开知识库");
        assertTrue(StringUtils.hasText(target.get("authorName").asText()), "应返回作者昵称");
        assertEquals(false, target.get("isFavorite").asBoolean());
        assertEquals(false, target.get("isOwner").asBoolean(), "B 视角不应是作者");

        // A 的公开列表应含自己的公开库且 isOwner=true（供前端显示“我的”徽标）
        String aResp = mockMvc.perform(get("/knowledge/public/list").header("Authorization", "Bearer " + a.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode targetA = findById(data(aResp), publicId);
        assertNotNull(targetA);
        assertEquals(true, targetA.get("isOwner").asBoolean(), "A 视角自己的公开库 isOwner 应为 true");
    }

    private JsonNode findById(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    // ---------- 收藏 / 取消收藏 ----------

    @Test
    void favorite_unfavorite_roundtrip() throws Exception {
        LoginUser a = registerAndLogin();
        long publicId = createKnowledge(a.token(), "A 公开库", 1);

        LoginUser b = registerAndLogin();
        String fav = mockMvc.perform(post("/knowledge/{id}/favorite", publicId)
                        .header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, code(fav), "收藏应成功");
        Integer favCount = jdbcTemplate.queryForObject(
                "SELECT favoriteCount FROM knowledge WHERE id = ?", Integer.class, publicId);
        assertEquals(1, favCount, "收藏后收藏量应为 1");

        // 收藏后出现在自己的知识库列表，isFavorite=true
        String list = mockMvc.perform(get("/knowledge/list").header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode item = findById(data(list), publicId);
        assertNotNull(item, "收藏的知识库应出现在我的列表");
        assertEquals(true, item.get("isFavorite").asBoolean());
        // 回归：收藏的是他人库，isOwner 必须为 false（否则前端显示“我的”）
        assertEquals(false, item.get("isOwner").asBoolean(), "收藏他人公开库不应标记为我的");

        // 取消收藏
        String unfav = mockMvc.perform(delete("/knowledge/{id}/favorite", publicId)
                        .header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, code(unfav));
        favCount = jdbcTemplate.queryForObject(
                "SELECT favoriteCount FROM knowledge WHERE id = ?", Integer.class, publicId);
        assertEquals(0, favCount, "取消后收藏量归 0");
        String list2 = mockMvc.perform(get("/knowledge/list").header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(findById(data(list2), publicId) == null, "取消收藏后列表不再包含该项");
    }

    @Test
    void favorite_rejectsOwnAndPrivate() throws Exception {
        LoginUser a = registerAndLogin();
        long privateId = createKnowledge(a.token(), "A 私有库", 0);

        // 收藏私有库被拒
        String resp1 = mockMvc.perform(post("/knowledge/{id}/favorite", privateId)
                        .header("Authorization", "Bearer " + a.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, code(resp1), "收藏他人私有库应被拒");

        // 收藏自己的公开库被拒
        long ownPublic = createKnowledge(a.token(), "A 自己公开库", 1);
        String resp2 = mockMvc.perform(post("/knowledge/{id}/favorite", ownPublic)
                        .header("Authorization", "Bearer " + a.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40000, code(resp2), "收藏自己的库应被拒");
    }

    // ---------- 详情浏览 + 浏览量 ----------

    @Test
    void detail_incrementsView_publicAccessible() throws Exception {
        LoginUser a = registerAndLogin();
        long publicId = createKnowledge(a.token(), "A 公开库", 1);
        long privateId = createKnowledge(a.token(), "A 私有库", 0);

        LoginUser b = registerAndLogin();
        // B 看公开库详情：code 0，viewCount +1
        String resp = mockMvc.perform(get("/knowledge/{id}", publicId).header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, code(resp));
        Integer views = jdbcTemplate.queryForObject(
                "SELECT viewCount FROM knowledge WHERE id = ?", Integer.class, publicId);
        assertEquals(1, views, "外部查看后浏览量应为 1");

        // B 看私有库详情：无权限
        String resp2 = mockMvc.perform(get("/knowledge/{id}", privateId).header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, code(resp2), "他人私有库不可访问");
    }

    // ---------- 复制 ----------

    @Test
    void copy_kb_createsIndependentCopy() throws Exception {
        LoginUser a = registerAndLogin();
        long publicId = createKnowledge(a.token(), "A 公开库", 1);
        upload(a.token(), publicId, "a.txt", "公开库内容".getBytes(StandardCharsets.UTF_8));
        Long sourceDocs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_doc WHERE knowledgeId = ?", Long.class, publicId);
        assertEquals(1, sourceDocs, "源库应含 1 个文档");

        LoginUser b = registerAndLogin();
        String resp = mockMvc.perform(post("/knowledge/{id}/copy", publicId)
                        .header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, code(resp));
        long copyId = objectMapper.readTree(resp).get("data").asLong();
        Long copyDocsDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_doc WHERE knowledgeId = ?", Long.class, copyId);
        assertEquals(1, copyDocsDb, "副本库应含 1 个文档（DB 直查）");

        // 副本归属 B、默认私有、名称带“（副本）”
        String name = jdbcTemplate.queryForObject("SELECT name FROM knowledge WHERE id = ?", String.class, copyId);
        Long ownerId = jdbcTemplate.queryForObject("SELECT userId FROM knowledge WHERE id = ?", Long.class, copyId);
        Integer isPublic = jdbcTemplate.queryForObject("SELECT isPublic FROM knowledge WHERE id = ?", Integer.class, copyId);
        assertEquals(b.id(), ownerId, "复制者应为新作者");
        assertEquals(0, isPublic, "副本默认私有");
        assertTrue(name.endsWith("（副本）"), "副本名称应带后缀，实际: " + name);

        // 副本文档：1 个、PENDING、有向量化任务
        String docs = mockMvc.perform(get("/knowledge/{id}/docs", copyId).header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode docList = data(docs);
        assertEquals(1, docList.size(), "副本应复制源库的 1 个文档");
        assertEquals("PENDING", docList.get(0).get("vectorStatus").asText(), "副本文档应待入库");

        // B 可对副本上传（已是 B 的库）
        String up = upload(b.token(), copyId, "b.txt", "副本新增内容".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, code(up));
        assertTrue(StringUtils.hasText(objectMapper.readTree(up).get("data").asText()), "副本上传应返回任务 id");
    }

    // ---------- 协作者 ----------

    @Test
    void member_invite_grantsDocPermission_ownerManages() throws Exception {
        LoginUser a = registerAndLogin();
        long kbId = createKnowledge(a.token(), "A 协作库", 0);
        LoginUser b = registerAndLogin();

        // 邀请前 B 上传被拒
        String before = upload(b.token(), kbId, "x.txt", "x".getBytes(StandardCharsets.UTF_8));
        assertEquals(40101, code(before), "非协作者不可上传");

        // A 按账号邀请 B
        String invite = mockMvc.perform(post("/knowledge/{id}/members", kbId)
                        .header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userAccount", b.account()))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, code(invite), "邀请协作者应成功");

        // B 成为协作者后可上传
        String after = upload(b.token(), kbId, "a.txt", "协作者上传内容".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, code(after), "协作者应可上传文档");
        assertTrue(StringUtils.hasText(objectMapper.readTree(after).get("data").asText()));

        // B 仍不能编辑知识库信息（作者专属）
        String upd = mockMvc.perform(post("/knowledge/update")
                        .header("Authorization", "Bearer " + b.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", String.valueOf(kbId), "name", "B 改名", "isPublic", 1))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, code(upd), "协作者不可编辑知识库信息");

        // A 移除 B 后，B 上传再次被拒
        String remove = mockMvc.perform(delete("/knowledge/{id}/members/{userId}", kbId, b.id())
                        .header("Authorization", "Bearer " + a.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, code(remove));
        String afterRemove = upload(b.token(), kbId, "b.txt", "移除后上传".getBytes(StandardCharsets.UTF_8));
        assertEquals(40101, code(afterRemove), "被移除后不可再上传");
    }

    @Test
    void memberManagement_ownerOnly() throws Exception {
        LoginUser a = registerAndLogin();
        long kbId = createKnowledge(a.token(), "A 协作库", 0);
        LoginUser b = registerAndLogin();

        String resp = mockMvc.perform(get("/knowledge/{id}/members", kbId).header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, code(resp), "非作者不可查看成员列表");
    }

    // ---------- 查看者只读 ----------

    @Test
    void viewer_docs_readonly() throws Exception {
        LoginUser a = registerAndLogin();
        long publicId = createKnowledge(a.token(), "A 公开库", 1);
        upload(a.token(), publicId, "normal.txt", "正常文档".getBytes(StandardCharsets.UTF_8));
        upload(a.token(), publicId, "delete.txt", "将被删除".getBytes(StandardCharsets.UTF_8));
        // 逻辑删除一个文档（走真实删除接口）；上传返回任务 id，需查库拿文档 id
        Long deletedDocId = jdbcTemplate.queryForObject(
                "SELECT id FROM knowledge_doc WHERE knowledgeId = ? AND name = 'delete.txt'", Long.class, publicId);
        mockMvc.perform(delete("/knowledge/{id}/docs/{docId}", publicId, deletedDocId)
                        .header("Authorization", "Bearer " + a.token()))
                .andReturn();

        LoginUser b = registerAndLogin();
        mockMvc.perform(post("/knowledge/{id}/favorite", publicId).header("Authorization", "Bearer " + b.token()))
                .andReturn();

        // 查看者只见正常文档（已删除被强制过滤）
        String docs = mockMvc.perform(get("/knowledge/{id}/docs", publicId).header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode list = data(docs);
        assertEquals(1, list.size(), "查看者只能看到正常文档");
        assertEquals("normal.txt", list.get(0).get("name").asText());

        // 查看者不能上传、不能删除知识库
        String up = upload(b.token(), publicId, "x.txt", "查看者上传".getBytes(StandardCharsets.UTF_8));
        assertEquals(40101, code(up), "仅查看者不可上传");
        String del = mockMvc.perform(delete("/knowledge/{id}", publicId).header("Authorization", "Bearer " + b.token()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, code(del), "仅查看者不可删除知识库");
    }

    // ---------- 编辑信息 ----------

    @Test
    void update_editsInfo_ownerOnly() throws Exception {
        LoginUser a = registerAndLogin();
        long kbId = createKnowledge(a.token(), "原名", 0);

        String resp = mockMvc.perform(post("/knowledge/update")
                        .header("Authorization", "Bearer " + a.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "id", String.valueOf(kbId),
                                "name", "新名",
                                "description", "新简介",
                                "cover", "/api/files/202608/1/uuid.png",
                                "isPublic", 1))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(0, code(resp), "作者应可更新知识库信息");
        assertEquals("新名", jdbcTemplate.queryForObject("SELECT name FROM knowledge WHERE id = ?", String.class, kbId));
        assertEquals("新简介", jdbcTemplate.queryForObject("SELECT description FROM knowledge WHERE id = ?", String.class, kbId));
        assertEquals("/api/files/202608/1/uuid.png", jdbcTemplate.queryForObject("SELECT cover FROM knowledge WHERE id = ?", String.class, kbId));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT isPublic FROM knowledge WHERE id = ?", Integer.class, kbId));

        // 他人更新被拒
        LoginUser b = registerAndLogin();
        String resp2 = mockMvc.perform(post("/knowledge/update")
                        .header("Authorization", "Bearer " + b.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", String.valueOf(kbId), "name", "B 改名"))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(40101, code(resp2), "非作者不可更新知识库信息");
    }
}
