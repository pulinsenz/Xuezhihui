package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.BatchDocRequest;
import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.req.KnowledgeUpdateRequest;
import com.agent.rag.dto.req.MemberInviteRequest;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.dto.resp.MemberVO;
import com.agent.rag.service.KnowledgeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * 更新知识库信息（名称/封面/简介/是否公开），仅作者
     */
    @PostMapping("/update")
    public Result<Boolean> update(@RequestBody KnowledgeUpdateRequest request) {
        knowledgeService.updateKnowledge(request);
        return Result.success(true);
    }

    /**
     * 我的知识库列表（我拥有的 + 我收藏的）
     */
    @GetMapping("/list")
    public Result<List<KnowledgeVO>> list() {
        return Result.success(knowledgeService.listMyKnowledge());
    }

    /**
     * 公开知识库列表（isPublic=1 且未删除），关键词可选
     */
    @GetMapping("/public/list")
    public Result<List<KnowledgeVO>> publicList(@RequestParam(required = false) String keyword) {
        return Result.success(knowledgeService.listPublicKnowledge(keyword));
    }

    /**
     * 知识库详情（作者/协作者/已收藏/公开可查看；外部查看浏览量 +1）
     */
    @GetMapping("/{id}")
    public Result<KnowledgeVO> detail(@PathVariable Long id) {
        return Result.success(knowledgeService.getKnowledgeDetail(id));
    }

    /**
     * 收藏知识库（仅他人公开库）
     */
    @PostMapping("/{id}/favorite")
    public Result<Boolean> favorite(@PathVariable Long id) {
        knowledgeService.favorite(id);
        return Result.success(true);
    }

    /**
     * 取消收藏知识库
     */
    @DeleteMapping("/{id}/favorite")
    public Result<Boolean> unfavorite(@PathVariable Long id) {
        knowledgeService.unfavorite(id);
        return Result.success(true);
    }

    /**
     * 复制知识库：复制者为新作者，返回新知识库 id
     */
    @PostMapping("/{id}/copy")
    public Result<Long> copy(@PathVariable Long id) {
        return Result.success(knowledgeService.copyKnowledge(id));
    }

    /**
     * 上传封面图片，返回可访问 URL（/api/files/...）
     */
    @PostMapping("/cover")
    public Result<String> uploadCover(@RequestPart("file") MultipartFile file) {
        return Result.success(knowledgeService.uploadCover(file));
    }

    /**
     * 协作者列表（仅作者）
     */
    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> members(@PathVariable Long id) {
        return Result.success(knowledgeService.listMembers(id));
    }

    /**
     * 邀请协作者（仅作者，按 userId 或 userAccount）
     */
    @PostMapping("/{id}/members")
    public Result<Boolean> addMember(@PathVariable Long id, @RequestBody MemberInviteRequest request) {
        knowledgeService.addMember(id, request);
        return Result.success(true);
    }

    /**
     * 移除协作者（仅作者）
     */
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Boolean> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        knowledgeService.removeMember(id, userId);
        return Result.success(true);
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
     * 同知识库内存在相同内容文件时返回 null（默认未入库，可强制入库）
     */
    @PostMapping("/{id}/upload")
    public Result<String> upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.success(knowledgeService.uploadDoc(id, file));
    }

    /**
     * 文档强制/重新入库：SKIPPED 重复文件强制向量化、FAILED/PENDING/REMOVED 重试；返回任务 id
     */
    @PostMapping("/{id}/docs/{docId}/revectorize")
    public Result<String> reVectorize(@PathVariable Long id, @PathVariable Long docId) {
        return Result.success(knowledgeService.reVectorizeDoc(id, docId));
    }

    /**
     * 移除入库：删除文档向量（保留文档记录），状态置为未入库
     */
    @PostMapping("/{id}/docs/{docId}/remove-vector")
    public Result<Boolean> removeVector(@PathVariable Long id, @PathVariable Long docId) {
        knowledgeService.removeVector(id, docId);
        return Result.success(true);
    }

    /**
     * 删除单个文档：逻辑删除（来源=用户）+ 删除该文档向量，用户可自恢复
     */
    @DeleteMapping("/{id}/docs/{docId}")
    public Result<Boolean> deleteDoc(@PathVariable Long id, @PathVariable Long docId) {
        knowledgeService.deleteDoc(id, docId);
        return Result.success(true);
    }

    /**
     * 彻底删除单个文档：逻辑删除并标记 purged，前端「已删除」列表不再展示、用户不可自恢复（普通/已删除文档均可）
     */
    @DeleteMapping("/{id}/docs/{docId}/purge")
    public Result<Boolean> purgeDoc(@PathVariable Long id, @PathVariable Long docId) {
        knowledgeService.purgeDoc(id, docId);
        return Result.success(true);
    }

    /**
     * 恢复用户自己删除的文档（管理员删除的拒绝），恢复后重新入库
     */
    @PostMapping("/{id}/docs/{docId}/restore")
    public Result<String> restoreDoc(@PathVariable Long id, @PathVariable Long docId) {
        return Result.success(knowledgeService.restoreDoc(id, docId));
    }

    /**
     * 批量移除入库：删除所选文档向量（保留文档记录），返回处理数量
     */
    @PostMapping("/{id}/docs/batch-remove-vector")
    public Result<Integer> batchRemoveVector(@PathVariable Long id, @RequestBody BatchDocRequest request) {
        return Result.success(knowledgeService.batchRemoveVector(id, request.getDocIds()));
    }

    /**
     * 批量删除文档：逻辑删除 + 删除各文档向量，返回删除数量
     */
    @PostMapping("/{id}/docs/batch-delete")
    public Result<Integer> batchDelete(@PathVariable Long id, @RequestBody BatchDocRequest request) {
        return Result.success(knowledgeService.batchDeleteDocs(id, request.getDocIds()));
    }

    /**
     * 批量入库：为所选文档逐个提交向量化任务（已入库/已删除自动跳过），返回任务 id 列表
     */
    @PostMapping("/{id}/docs/batch-vectorize")
    public Result<List<String>> batchVectorize(@PathVariable Long id, @RequestBody BatchDocRequest request) {
        return Result.success(knowledgeService.batchVectorize(id, request.getDocIds()));
    }

    /**
     * 知识库文档列表（deleted 过滤：null=全部/0=正常/1=已删除，用户可查看自己删除的文档并恢复；
     * category 过滤：null/all=全部, vectorized=已入库, unvectorized=未入库。
     * 外部查看者（非作者/协作者）强制只看正常文档）
     */
    @GetMapping("/{id}/docs")
    public Result<List<KnowledgeDocVO>> docs(@PathVariable Long id,
                                             @RequestParam(required = false) Integer deleted,
                                             @RequestParam(required = false) String category) {
        return Result.success(knowledgeService.listDocs(id, deleted, category));
    }
}
