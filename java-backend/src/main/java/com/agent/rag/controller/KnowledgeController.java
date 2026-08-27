package com.agent.rag.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.Result;
import com.agent.rag.dto.req.BatchDocRequest;
import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.req.KnowledgeUpdateRequest;
import com.agent.rag.dto.req.MemberInviteRequest;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.dto.resp.MemberVO;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.service.KnowledgeService;
import com.agent.rag.storage.FileStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

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

    @Resource
    private FileStorageService fileStorageService;

    /**
     * 危险文本类型：浏览器可将其作为活动 HTML 渲染并执行脚本，若以 inline 同源展示会形成存储型 XSS。
     * （SVG 已在上传白名单排除，此处兜底覆盖 html/xml/htm/xhtml/svg：一律强制下载，绝不 inline 渲染）
     */
    private static final Set<String> UNSAFE_INLINE_EXTS = Set.of("html", "htm", "xhtml", "xml", "svg");

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

    /**
     * 文档内容文件（原始文件，inline 预览），供「打开」；可查看者均可访问
     */
    @GetMapping("/{id}/docs/{docId}/file")
    public ResponseEntity<org.springframework.core.io.Resource> docFile(@PathVariable Long id, @PathVariable Long docId) {
        return buildFileResponse(knowledgeService.getViewableDoc(id, docId), true);
    }

    /**
     * 文档内容文件（原始文件，attachment 下载），供「下载」；可查看者均可访问
     */
    @GetMapping("/{id}/docs/{docId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> docDownload(@PathVariable Long id, @PathVariable Long docId) {
        return buildFileResponse(knowledgeService.getViewableDoc(id, docId), false);
    }

    /**
     * 文档切片详情（文本列表），供「向量详情」查看；可查看者均可访问
     */
    @GetMapping("/{id}/docs/{docId}/chunks")
    public Result<List<String>> docChunks(@PathVariable Long id, @PathVariable Long docId) {
        return Result.success(knowledgeService.listDocChunks(id, docId));
    }

    /**
     * 组装文件响应：inline=浏览器内预览（PDF/文本），attachment=触发下载。
     * 文件名按 RFC 5987（filename*=UTF-8''）编码，中文不乱码。
     * fileUrl 为对象存储（COS）地址时经 Java 服务端流式代理转发，避免 302 直跳被前端 CORS 拦截。
     */
    private ResponseEntity<org.springframework.core.io.Resource> buildFileResponse(KnowledgeDoc doc, boolean inline) {
        if (StrUtil.isBlank(doc.getFileUrl())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件地址为空");
        }
        String encodedName = URLEncoder.encode(doc.getName(), StandardCharsets.UTF_8).replace("+", "%20");
        // 危险文本类型（html/xml/svg 等）强制 attachment 下载，绝不 inline 渲染，杜绝存储型 XSS
        boolean unsafeInline = UNSAFE_INLINE_EXTS.contains(FileUtil.extName(doc.getName()).toLowerCase());
        String disposition = ((inline && !unsafeInline) ? "inline" : "attachment")
                + "; filename*=UTF-8''" + encodedName;
        MediaType mediaType = MediaTypeFactory.getMediaType(doc.getName())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        // 安全响应头：X-Content-Type-Options 禁止 MIME 嗅探（文本被当 HTML 执行）；危险类型再叠加 CSP sandbox 兜底
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
        headers.add("X-Content-Type-Options", "nosniff");
        if (unsafeInline) {
            headers.add("Content-Security-Policy", "sandbox; default-src 'none'");
        }

        String fileUrl = doc.getFileUrl();
        if (StrUtil.startWithIgnoreCase(fileUrl, "http://")
                || StrUtil.startWithIgnoreCase(fileUrl, "https://")) {
            // 对象存储（如 COS）：服务端拉取内容流并流式返回，Spring 写完响应自动关闭流
            try {
                InputStream in = fileStorageService.open(fileUrl);
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(new InputStreamResource(in));
            } catch (IOException e) {
                log.error("流式代理对象存储文件失败: url={}", fileUrl, e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件读取失败");
            }
        }

        File file = new File(fileUrl);
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文件不存在或已被清理");
        }
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(file.length())
                .body(new FileSystemResource(file));
    }
}
