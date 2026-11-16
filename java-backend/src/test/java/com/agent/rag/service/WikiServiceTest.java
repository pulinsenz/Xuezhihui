package com.agent.rag.service;

import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.req.WikiCallbackRequest;
import com.agent.rag.dto.req.WikiUpdateRequest;
import com.agent.rag.dto.resp.WikiPageVO;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.WikiPage;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.WikiPageMapper;
import com.agent.rag.service.impl.WikiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI Wiki 服务单元测试（Mockito，不连 Redis/DB）
 * <p>
 * 覆盖：生成任务提交（无文档拒绝/权限校验）、页面列表与详情（权限 + 跨库枚举拦截）、
 * 手动编辑字段语义、删除 Wiki（向量清理降级容忍）、生成回调落库（整体替换 / 失败保留旧页）。
 *
 * @author pulinsenz
 */
@ExtendWith(MockitoExtension.class)
class WikiServiceTest {

    @Mock
    private KnowledgeService knowledgeService;
    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;
    @Mock
    private WikiPageMapper wikiPageMapper;
    @Mock
    private TaskService taskService;
    @Mock
    private PythonAgentClient pythonAgentClient;

    private WikiServiceImpl wikiService;

    @BeforeEach
    void setUp() {
        wikiService = new WikiServiceImpl();
        ReflectionTestUtils.setField(wikiService, "knowledgeService", knowledgeService);
        ReflectionTestUtils.setField(wikiService, "knowledgeDocMapper", knowledgeDocMapper);
        ReflectionTestUtils.setField(wikiService, "wikiPageMapper", wikiPageMapper);
        ReflectionTestUtils.setField(wikiService, "taskService", taskService);
        ReflectionTestUtils.setField(wikiService, "pythonAgentClient", pythonAgentClient);
    }

    // ---------- 生成任务提交 ----------

    @Test
    void generateWiki_publishesTaskAndReturnsTaskId() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(11L);
        doc.setKnowledgeId(5L);
        doc.setName("course.md");
        doc.setFileUrl("/data/files/course.md");
        when(knowledgeDocMapper.selectList(any())).thenReturn(List.of(doc));
        when(taskService.publishWiki(anyLong(), anyList())).thenReturn("task-1");

        String taskId = wikiService.generateWiki(5L);

        assertEquals("task-1", taskId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KnowledgeDoc>> docsCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskService).publishWiki(org.mockito.ArgumentMatchers.eq(5L), docsCaptor.capture());
        assertEquals(1, docsCaptor.getValue().size());
        assertEquals(11L, docsCaptor.getValue().get(0).getId());
    }

    @Test
    void generateWiki_noDocs_throwsParamsError() {
        when(knowledgeDocMapper.selectList(any())).thenReturn(List.of());
        BusinessException e = assertThrows(BusinessException.class, () -> wikiService.generateWiki(5L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        verify(taskService, never()).publishWiki(anyLong(), anyList());
    }

    @Test
    void generateWiki_noManagePermission_propagates() {
        when(knowledgeService.getManageableKnowledge(5L))
                .thenThrow(new BusinessException(ErrorCode.NO_AUTH, "无权操作该知识库"));
        BusinessException e = assertThrows(BusinessException.class, () -> wikiService.generateWiki(5L));
        assertEquals(ErrorCode.NO_AUTH.getCode(), e.getCode());
        verify(knowledgeDocMapper, never()).selectList(any());
    }

    // ---------- 页面列表 / 详情 ----------

    @Test
    void listPages_viewableGate_orderedAndWithoutContent() {
        when(knowledgeService.getViewableKnowledge(5L)).thenReturn(new Knowledge());
        // 服务层信任 SQL 的 ORDER BY sortOrder（查询里 orderByAsc），mock 按 DB 返回顺序给
        WikiPage p1 = page(1L, 5L, "总览", "正文一", 1);
        WikiPage p2 = page(2L, 5L, "详解", "正文二", 2);
        when(wikiPageMapper.selectList(any())).thenReturn(List.of(p1, p2));

        List<WikiPageVO> vos = wikiService.listPages(5L);

        assertEquals(2, vos.size());
        assertEquals(1, vos.get(0).getSortOrder());
        assertEquals("总览", vos.get(0).getTitle());
        assertNull(vos.get(0).getContent(), "列表接口不应携带正文");
    }

    @Test
    void getPage_pageNotInKnowledge_throwsNotFound() {
        when(knowledgeService.getViewableKnowledge(5L)).thenReturn(new Knowledge());
        when(wikiPageMapper.selectById(9L)).thenReturn(page(9L, 6L, "他库页面", null, 1));
        BusinessException e = assertThrows(BusinessException.class, () -> wikiService.getPage(5L, 9L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void getPage_pageFailed_carriesStatusAndError() {
        when(knowledgeService.getViewableKnowledge(5L)).thenReturn(new Knowledge());
        WikiPage page = page(1L, 5L, "失败页", null, 1);
        page.setErrorMsg("LLM 调用失败");
        when(wikiPageMapper.selectById(1L)).thenReturn(page);

        WikiPageVO vo = wikiService.getPage(5L, 1L);
        assertEquals("FAILED", vo.getStatus());
        assertEquals("LLM 调用失败", vo.getErrorMsg());
    }

    // ---------- 手动编辑 ----------

    @Test
    void updatePage_updatesTitleAndContent() {
        when(knowledgeService.getManageableKnowledge(5L)).thenReturn(new Knowledge());
        WikiPage page = page(1L, 5L, "旧标题", "旧正文", 1);
        when(wikiPageMapper.selectById(1L)).thenReturn(page);
        when(wikiPageMapper.updateById(any(WikiPage.class))).thenReturn(1);

        WikiUpdateRequest req = new WikiUpdateRequest();
        req.setTitle("新标题");
        req.setContent("新正文");
        assertTrue(wikiService.updatePage(5L, 1L, req));

        ArgumentCaptor<WikiPage> captor = ArgumentCaptor.forClass(WikiPage.class);
        verify(wikiPageMapper).updateById(captor.capture());
        assertEquals("新标题", captor.getValue().getTitle());
        assertEquals("新正文", captor.getValue().getContent());
    }

    @Test
    void updatePage_blankTitle_keepsOldTitle() {
        when(knowledgeService.getManageableKnowledge(5L)).thenReturn(new Knowledge());
        WikiPage page = page(1L, 5L, "旧标题", "旧正文", 1);
        when(wikiPageMapper.selectById(1L)).thenReturn(page);
        when(wikiPageMapper.updateById(any(WikiPage.class))).thenReturn(1);

        WikiUpdateRequest req = new WikiUpdateRequest();
        req.setTitle("  ");
        wikiService.updatePage(5L, 1L, req);

        ArgumentCaptor<WikiPage> captor = ArgumentCaptor.forClass(WikiPage.class);
        verify(wikiPageMapper).updateById(captor.capture());
        assertNull(captor.getValue().getTitle(), "空白标题不应更新");
    }

    // ---------- 删除 Wiki ----------

    @Test
    void deleteWiki_cleansVectorsBestEffortAndDeletesRows() {
        when(knowledgeService.getManageableKnowledge(5L)).thenReturn(new Knowledge());
        WikiPage withVector = page(1L, 5L, "p1", null, 1);
        withVector.setVectorDocId("wiki-abc");
        WikiPage noVector = page(2L, 5L, "p2", null, 2);
        when(wikiPageMapper.selectList(any())).thenReturn(List.of(withVector, noVector));
        // 第一页清向量网络异常：降级容忍，不阻塞删除
        doThrow(new RuntimeException("feign down")).when(pythonAgentClient)
                .deleteKnowledge(any(DeleteVectorRequest.class));

        wikiService.deleteWiki(5L);

        verify(pythonAgentClient).deleteKnowledge(
                org.mockito.ArgumentMatchers.argThat((DeleteVectorRequest r) ->
                        "wiki-abc".equals(r.getDocId()) && "5".equals(r.getKnowledgeId())));
        verify(wikiPageMapper).delete(any());
    }

    // ---------- 生成回调落库 ----------

    @Test
    void completeWikiCallback_success_replacesPagesInOrder() {
        WikiCallbackRequest req = new WikiCallbackRequest();
        req.setTaskId("task-1");
        req.setKnowledgeId("5");
        req.setStatus("SUCCESS");
        req.setPages(List.of(item("页面B", "wiki-b"), item("页面A", "wiki-a")));

        wikiService.completeWikiCallback(req);

        // 整体替换：先逻辑删旧页
        verify(wikiPageMapper).delete(any());
        ArgumentCaptor<WikiPage> captor = ArgumentCaptor.forClass(WikiPage.class);
        verify(wikiPageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<WikiPage> inserted = captor.getAllValues();
        assertEquals("页面B", inserted.get(0).getTitle());
        assertEquals(1, inserted.get(0).getSortOrder(), "按回调顺序重排序号");
        assertEquals("wiki-b", inserted.get(0).getVectorDocId());
        assertEquals("页面A", inserted.get(1).getTitle());
        assertEquals(2, inserted.get(1).getSortOrder());
    }

    @Test
    void completeWikiCallback_failed_keepsOldPages() {
        WikiCallbackRequest req = new WikiCallbackRequest();
        req.setKnowledgeId("5");
        req.setStatus("FAILED");
        req.setMessage("LLM 调用失败");

        wikiService.completeWikiCallback(req);

        verify(wikiPageMapper, never()).delete(any());
        verify(wikiPageMapper, never()).insert(any(WikiPage.class));
    }

    @Test
    void completeWikiCallback_blankParams_throwsParamsError() {
        WikiCallbackRequest req = new WikiCallbackRequest();
        BusinessException e = assertThrows(BusinessException.class, () -> wikiService.completeWikiCallback(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void completeWikiCallback_badKnowledgeId_throwsParamsError() {
        WikiCallbackRequest req = new WikiCallbackRequest();
        req.setKnowledgeId("not-a-number");
        req.setStatus("SUCCESS");
        BusinessException e = assertThrows(BusinessException.class, () -> wikiService.completeWikiCallback(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    // ---------- 辅助 ----------

    private WikiPage page(Long id, Long knowledgeId, String title, String content, int order) {
        WikiPage page = new WikiPage();
        page.setId(id);
        page.setKnowledgeId(knowledgeId);
        page.setTitle(title);
        page.setContent(content);
        page.setSortOrder(order);
        page.setUpdateTime(LocalDateTime.now());
        return page;
    }

    private WikiCallbackRequest.WikiPageItem item(String title, String vectorDocId) {
        WikiCallbackRequest.WikiPageItem item = new WikiCallbackRequest.WikiPageItem();
        item.setTitle(title);
        item.setSummary(title + "摘要");
        item.setContent("# " + title);
        item.setSourceDocIds("[\"11\"]");
        item.setVectorDocId(vectorDocId);
        item.setStatus("SUCCESS");
        return item;
    }
}
