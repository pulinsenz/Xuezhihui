package com.agent.rag.controller;

import com.agent.rag.mapper.WikiPageMapper;
import com.agent.rag.entity.WikiPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Python worker Wiki 生成完成回调接口测试（MockMvc + 真实 MySQL）
 * <p>
 * 覆盖：X-Agent-Token 鉴权（缺失/错误拒绝）、SUCCESS 回写页面（整体替换）、
 * FAILED 不动现有页面。wiki_page 表由 WikiPageInitializer 启动时确保存在。
 *
 * @author pulinsenz
 */
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "jwt.secret=test-secret-for-integration-tests-0123456789abcdef0123456789abcdef",
        "app.agent.token=test-agent-token"
})
@AutoConfigureMockMvc
class WikiCallbackControllerTest {

    private static final String TOKEN = "test-agent-token";
    /** 测试专用 knowledgeId（无外键约束，直接使用，测试后按 id 清理） */
    private static final long KB_ID = 987654321L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private WikiPageMapper wikiPageMapper;

    private final List<Long> createdPageIds = new java.util.ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long pageId : createdPageIds) {
            jdbcTemplate.update("DELETE FROM wiki_page WHERE id = ?", pageId);
        }
        createdPageIds.clear();
    }

    private String callCallback(Map<String, Object> body, String token) throws Exception {
        return mockMvc.perform(post("/internal/agent/wiki-callback")
                        .header("X-Agent-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void seedPage(long knowledgeId, String title) {
        WikiPage page = new WikiPage();
        page.setKnowledgeId(knowledgeId);
        page.setTitle(title);
        page.setContent("旧内容");
        page.setSortOrder(1);
        wikiPageMapper.insert(page);
        createdPageIds.add(page.getId());
    }

    @Test
    void callback_success_replacesPages() throws Exception {
        seedPage(KB_ID, "旧页面");

        String body = callCallback(Map.of(
                "task_id", "task-wiki-1",
                "knowledge_id", String.valueOf(KB_ID),
                "status", "SUCCESS",
                "pages", List.of(Map.of(
                        "title", "系统总览",
                        "summary", "总览摘要",
                        "content", "# 系统总览",
                        "source_doc_ids", "[\"1\"]",
                        "vector_doc_id", "wiki-test-1",
                        "status", "SUCCESS"))), TOKEN);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());

        List<WikiPage> pages = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getKnowledgeId, KB_ID));
        assertEquals(1, pages.size(), "SUCCESS 回调应整体替换旧页面");
        assertEquals("系统总览", pages.get(0).getTitle());
        assertEquals("wiki-test-1", pages.get(0).getVectorDocId());
        createdPageIds.add(pages.get(0).getId());
    }

    @Test
    void callback_failed_keepsExistingPages() throws Exception {
        seedPage(KB_ID, "旧页面");

        String body = callCallback(Map.of(
                "task_id", "task-wiki-2",
                "knowledge_id", String.valueOf(KB_ID),
                "status", "FAILED",
                "message", "LLM 调用失败"), TOKEN);
        assertEquals(0, objectMapper.readTree(body).get("code").asInt());

        List<WikiPage> pages = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getKnowledgeId, KB_ID));
        assertEquals(1, pages.size(), "FAILED 回调不应动现有页面");
        assertEquals("旧页面", pages.get(0).getTitle());
    }

    @Test
    void callback_missingToken_rejected() throws Exception {
        String body = callCallback(Map.of(
                "knowledge_id", String.valueOf(KB_ID), "status", "SUCCESS", "pages", List.of()), "");
        assertEquals(40101, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void callback_wrongToken_rejected() throws Exception {
        String body = callCallback(Map.of(
                "knowledge_id", String.valueOf(KB_ID), "status", "SUCCESS", "pages", List.of()), "wrong-token");
        assertEquals(40101, objectMapper.readTree(body).get("code").asInt());
    }

    @Test
    void callback_badParams_rejectedWithParamsError() throws Exception {
        String body = callCallback(Map.of("status", "SUCCESS"), TOKEN);
        assertTrue(objectMapper.readTree(body).get("code").asInt() != 0, "缺 knowledge_id 应被拒绝");
    }
}
