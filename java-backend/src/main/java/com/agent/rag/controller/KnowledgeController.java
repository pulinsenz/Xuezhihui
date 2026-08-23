package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.service.KnowledgeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库接口
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    @Resource
    private KnowledgeService knowledgeService;

    /**
     * 创建知识库
     */
    @PostMapping("/create")
    public Result<Long> create(@RequestBody KnowledgeCreateRequest request) {
        return Result.success(knowledgeService.createKnowledge(request));
    }

    /**
     * 我的知识库列表
     */
    @GetMapping("/list")
    public Result<List<KnowledgeVO>> list() {
        return Result.success(knowledgeService.listMyKnowledge());
    }

    /**
     * 知识库详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeVO> detail(@PathVariable Long id) {
        return Result.success(KnowledgeVO.from(knowledgeService.getOwnedKnowledge(id)));
    }

    /**
     * 删除知识库（含文档）
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        knowledgeService.deleteKnowledge(id);
        return Result.success(true);
    }

    /**
     * 上传文档（multipart，字段名 file）：提交向量化任务，返回任务 id 供前端轮询 /task/{taskId}
     */
    @PostMapping("/{id}/upload")
    public Result<String> upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.success(knowledgeService.uploadDoc(id, file));
    }

    /**
     * 知识库文档列表
     */
    @GetMapping("/{id}/docs")
    public Result<List<KnowledgeDocVO>> docs(@PathVariable Long id) {
        return Result.success(knowledgeService.listDocs(id));
    }
}
