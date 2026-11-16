package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.WikiUpdateRequest;
import com.agent.rag.dto.resp.WikiPageVO;
import com.agent.rag.service.WikiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI Wiki 接口：知识库文档 → LLM 生成的结构化知识页
 * <p>
 * 查看（列表/详情）门槛与知识库一致（作者/协作者/收藏者/公开库）；
 * 生成/编辑/删除限作者与协作者。生成走异步任务，前端轮询 /task/{taskId}。
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/knowledge")
public class WikiController {

    @Resource
    private WikiService wikiService;

    /**
     * 生成（或重新生成）知识库 Wiki，返回任务 id 供前端轮询 /task/{taskId}
     */
    @PostMapping("/{id}/wiki/generate")
    public Result<String> generate(@PathVariable Long id) {
        return Result.success(wikiService.generateWiki(id));
    }

    /**
     * Wiki 页面列表（不含正文），按 sortOrder 升序；未生成时返回空数组
     */
    @GetMapping("/{id}/wiki")
    public Result<List<WikiPageVO>> list(@PathVariable Long id) {
        return Result.success(wikiService.listPages(id));
    }

    /**
     * Wiki 页面详情（含 Markdown 正文）
     */
    @GetMapping("/{id}/wiki/{pageId}")
    public Result<WikiPageVO> detail(@PathVariable Long id, @PathVariable Long pageId) {
        return Result.success(wikiService.getPage(id, pageId));
    }

    /**
     * 手动编辑页面（标题/正文），限作者与协作者
     */
    @PutMapping("/{id}/wiki/{pageId}")
    public Result<Boolean> update(@PathVariable Long id, @PathVariable Long pageId,
                                  @RequestBody WikiUpdateRequest request) {
        return Result.success(wikiService.updatePage(id, pageId, request));
    }

    /**
     * 删除整个 Wiki（清向量 + 逻辑删页面），限作者与协作者
     */
    @DeleteMapping("/{id}/wiki")
    public Result<Boolean> delete(@PathVariable Long id) {
        wikiService.deleteWiki(id);
        return Result.success(true);
    }
}
