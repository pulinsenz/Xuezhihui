package com.agent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.req.WikiCallbackRequest;
import com.agent.rag.dto.req.WikiUpdateRequest;
import com.agent.rag.dto.resp.WikiPageVO;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.WikiPage;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.WikiPageMapper;
import com.agent.rag.service.KnowledgeService;
import com.agent.rag.service.TaskService;
import com.agent.rag.service.WikiService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Wiki 服务实现。
 * <p>
 * 生成走异步长任务（TaskService.publishWiki → Redis 队列 → Python Wiki worker），
 * 本类只做权限校验、任务提交、页面读写与回调落库；Python 不直连 MySQL。
 * 页面查看门槛与知识库一致（作者/协作者/收藏者/公开库），管理操作限作者/协作者。
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class WikiServiceImpl implements WikiService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private KnowledgeService knowledgeService;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    @Resource
    private WikiPageMapper wikiPageMapper;

    @Resource
    private TaskService taskService;

    @Resource
    private PythonAgentClient pythonAgentClient;

    @Override
    public String generateWiki(Long knowledgeId) {
        knowledgeService.getManageableKnowledge(knowledgeId);
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getKnowledgeId, knowledgeId)
                .isNotNull(KnowledgeDoc::getFileUrl)
                .orderByAsc(KnowledgeDoc::getCreateTime));
        if (docs.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库中没有可用的文档，请先上传文档");
        }
        // 长任务走 Redis 消息队列：Python worker 消费（解析 → LLM 大纲 → 逐页撰写），
        // 完成后回调 Java 回写 wiki_page；前端轮询 /task/{taskId}
        return taskService.publishWiki(knowledgeId, docs);
    }

    @Override
    public List<WikiPageVO> listPages(Long knowledgeId) {
        knowledgeService.getViewableKnowledge(knowledgeId);
        List<WikiPage> pages = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getKnowledgeId, knowledgeId)
                .orderByAsc(WikiPage::getSortOrder));
        List<WikiPageVO> vos = new ArrayList<>();
        for (WikiPage page : pages) {
            vos.add(toVO(page, false));
        }
        return vos;
    }

    @Override
    public WikiPageVO getPage(Long knowledgeId, Long pageId) {
        knowledgeService.getViewableKnowledge(knowledgeId);
        WikiPage page = getPageInKnowledge(knowledgeId, pageId);
        return toVO(page, true);
    }

    @Override
    public boolean updatePage(Long knowledgeId, Long pageId, WikiUpdateRequest request) {
        knowledgeService.getManageableKnowledge(knowledgeId);
        WikiPage page = getPageInKnowledge(knowledgeId, pageId);
        WikiPage update = new WikiPage();
        update.setId(page.getId());
        if (StrUtil.isNotBlank(request.getTitle())) {
            update.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null) {
            // 内容允许显式清空，故仅判 null；手动编辑不触发重向量化（wiki 向量仍为生成时版本）
            update.setContent(request.getContent());
        }
        return wikiPageMapper.updateById(update) > 0;
    }

    @Override
    public void deleteWiki(Long knowledgeId) {
        knowledgeService.getManageableKnowledge(knowledgeId);
        List<WikiPage> pages = wikiPageMapper.selectList(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getKnowledgeId, knowledgeId));
        // 清 Milvus 中 wiki 向量（doc_id=wiki-{uuid}），降级容忍：向量失败不阻塞元数据删除
        for (WikiPage page : pages) {
            if (StrUtil.isBlank(page.getVectorDocId())) {
                continue;
            }
            try {
                pythonAgentClient.deleteKnowledge(new DeleteVectorRequest(
                        String.valueOf(knowledgeId), page.getVectorDocId()));
            } catch (Exception e) {
                log.warn("Wiki 向量清理失败（已降级）: knowledgeId={}, pageId={}, error={}",
                        knowledgeId, page.getId(), e.getMessage());
            }
        }
        wikiPageMapper.delete(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getKnowledgeId, knowledgeId));
        log.info("删除 Wiki 完成: knowledgeId={}, pages={}", knowledgeId, pages.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeWikiCallback(WikiCallbackRequest request) {
        if (request == null || StrUtil.isBlank(request.getKnowledgeId()) || StrUtil.isBlank(request.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回调参数错误");
        }
        Long knowledgeId;
        try {
            knowledgeId = Long.valueOf(request.getKnowledgeId());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回调 knowledgeId 非法");
        }
        if (STATUS_FAILED.equals(request.getStatus())) {
            // 任务整体失败：旧 Wiki 保留可见，仅记录日志（失败详情在 Redis 任务 Hash，前端轮询可见）
            log.warn("Wiki 生成失败（旧 Wiki 保留）: knowledgeId={}, taskId={}, message={}",
                    knowledgeId, request.getTaskId(), request.getMessage());
            return;
        }
        // 整体替换：逻辑删旧页 → 插入新页（同事务，中途失败整体回滚不留半新半旧）
        wikiPageMapper.delete(new LambdaQueryWrapper<WikiPage>()
                .eq(WikiPage::getKnowledgeId, knowledgeId));
        List<WikiCallbackRequest.WikiPageItem> pages = request.getPages();
        if (pages == null) {
            return;
        }
        int order = 1;
        for (WikiCallbackRequest.WikiPageItem item : pages) {
            WikiPage page = new WikiPage();
            page.setKnowledgeId(knowledgeId);
            page.setTitle(StrUtil.blankToDefault(item.getTitle(), "未命名页面"));
            page.setSummary(item.getSummary());
            page.setContent(item.getContent());
            page.setSourceDocIds(item.getSourceDocIds());
            page.setVectorDocId(item.getVectorDocId());
            page.setSortOrder(order++);
            page.setErrorMsg(item.getErrorMsg());
            wikiPageMapper.insert(page);
        }
        log.info("Wiki 生成回写完成: knowledgeId={}, taskId={}, pages={}",
                knowledgeId, request.getTaskId(), pages.size());
    }

    /**
     * 校验页面归属（防跨知识库枚举页面 id）
     */
    private WikiPage getPageInKnowledge(Long knowledgeId, Long pageId) {
        WikiPage page = wikiPageMapper.selectById(pageId);
        if (page == null || !page.getKnowledgeId().equals(knowledgeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Wiki 页面不存在");
        }
        return page;
    }

    /**
     * 实体 → VO；includeContent=false 时正文置空（列表接口瘦身）
     */
    private WikiPageVO toVO(WikiPage page, boolean includeContent) {
        WikiPageVO vo = new WikiPageVO();
        vo.setId(page.getId());
        vo.setTitle(page.getTitle());
        vo.setSummary(page.getSummary());
        if (includeContent) {
            vo.setContent(page.getContent());
        }
        vo.setSourceDocIds(page.getSourceDocIds());
        vo.setSortOrder(page.getSortOrder());
        boolean pageFailed = StrUtil.isNotBlank(page.getErrorMsg());
        vo.setStatus(pageFailed ? STATUS_FAILED : STATUS_SUCCESS);
        vo.setErrorMsg(page.getErrorMsg());
        vo.setUpdateTime(page.getUpdateTime() == null ? null : page.getUpdateTime().toString());
        return vo;
    }
}
