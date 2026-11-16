package com.agent.rag.service;

import com.agent.rag.dto.req.WikiCallbackRequest;
import com.agent.rag.dto.req.WikiUpdateRequest;
import com.agent.rag.dto.resp.WikiPageVO;

import java.util.List;

/**
 * AI Wiki 服务：知识库文档 → LLM 大纲规划 + 逐页撰写 → 结构化知识页
 *
 * @author pulinsenz
 */
public interface WikiService {

    /**
     * 生成（或重新生成）知识库 Wiki：提交异步任务，返回任务 id 供前端轮询。
     * 生成成功后整体替换旧 Wiki；失败则旧 Wiki 保留可见。
     */
    String generateWiki(Long knowledgeId);

    /**
     * Wiki 页面列表（不含正文），按 sortOrder 升序
     */
    List<WikiPageVO> listPages(Long knowledgeId);

    /**
     * 单页详情（含 Markdown 正文）
     */
    WikiPageVO getPage(Long knowledgeId, Long pageId);

    /**
     * 手动编辑页面（标题/正文）
     */
    boolean updatePage(Long knowledgeId, Long pageId, WikiUpdateRequest request);

    /**
     * 删除整个 Wiki：清 Milvus 中 wiki 向量（降级容忍）+ 逻辑删页面行
     */
    void deleteWiki(Long knowledgeId);

    /**
     * Python worker 完成回调：SUCCESS 时逻辑删旧 Wiki + 插入新页；FAILED 仅记日志
     */
    void completeWikiCallback(WikiCallbackRequest request);
}
