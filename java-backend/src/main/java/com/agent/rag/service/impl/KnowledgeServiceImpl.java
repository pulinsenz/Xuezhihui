package com.agent.rag.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.VectorStatus;
import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.UserStatsVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.agent.rag.mapper.ForbiddenFileHashMapper;
import com.agent.rag.service.KnowledgeService;
import com.agent.rag.service.TaskService;
import com.agent.rag.storage.FileStorageService;
import com.agent.rag.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务实现
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Resource
    private KnowledgeMapper knowledgeMapper;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    @Resource
    private ForbiddenFileHashMapper forbiddenFileHashMapper;

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private PythonAgentClient pythonAgentClient;

    @Resource
    private TaskService taskService;

    @Override
    public Long createKnowledge(KnowledgeCreateRequest request) {
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库名称不能为空");
        }
        Knowledge knowledge = new Knowledge();
        knowledge.setName(request.getName());
        knowledge.setDescription(request.getDescription());
        knowledge.setCover(request.getCover());
        knowledge.setUserId(UserContext.getUser().getId());
        knowledgeMapper.insert(knowledge);
        log.info("创建知识库成功: knowledgeId={}, userId={}", knowledge.getId(), knowledge.getUserId());
        return knowledge.getId();
    }

    @Override
    public List<KnowledgeVO> listMyKnowledge() {
        Long userId = UserContext.getUser().getId();
        List<Knowledge> list = knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getUserId, userId)
                .orderByDesc(Knowledge::getCreateTime));
        return list.stream().map(knowledge -> {
            KnowledgeVO vo = KnowledgeVO.from(knowledge);
            Long docCount = knowledgeDocMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                    .eq(KnowledgeDoc::getKnowledgeId, knowledge.getId()));
            vo.setDocCount(docCount);
            return vo;
        }).toList();
    }

    @Override
    public Knowledge getOwnedKnowledge(Long knowledgeId) {
        if (knowledgeId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库 id 不能为空");
        }
        Knowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        if (!knowledge.getUserId().equals(UserContext.getUser().getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权操作该知识库");
        }
        return knowledge;
    }

    @Override
    public void deleteKnowledge(Long knowledgeId) {
        getOwnedKnowledge(knowledgeId);
        knowledgeMapper.deleteById(knowledgeId);
        knowledgeDocMapper.delete(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getKnowledgeId, knowledgeId));
        // 清理向量库（Python Agent），降级：失败不影响元数据删除
        try {
            pythonAgentClient.deleteKnowledge(new DeleteVectorRequest(String.valueOf(knowledgeId), null));
            log.info("向量库清理成功: knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            log.warn("向量库清理失败（已降级）: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
        log.info("删除知识库成功: knowledgeId={}", knowledgeId);
    }

    @Override
    public String uploadDoc(Long knowledgeId, MultipartFile file) {
        getOwnedKnowledge(knowledgeId);
        // 计算文件内容 SHA-256（先算哈希再落盘，供同文件去重）
        String fileHash = computeFileHash(file);
        // 该文件被管理员删除（封禁哈希黑名单 或 同库历史 admin 删除记录）→ 禁止用户再上传
        // 保留库内判断兜底历史数据：旧版管理员删除未写入黑名单表，仅凭 deleteSource=admin 也能拦截
        Long adminDeleted = knowledgeDocMapper.countAdminDeletedByHash(knowledgeId, fileHash);
        boolean banned = forbiddenFileHashMapper.existsByHash(fileHash)
                || (adminDeleted != null && adminDeleted > 0);
        if (banned) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该文件已被管理员删除，禁止上传");
        }
        boolean duplicate = knowledgeDocMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getKnowledgeId, knowledgeId)
                .eq(KnowledgeDoc::getFileHash, fileHash)) > 0;
        String fileUrl = fileStorageService.store(file, UserContext.getUser().getId());
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setKnowledgeId(knowledgeId);
        doc.setName(file.getOriginalFilename());
        doc.setFileUrl(fileUrl);
        doc.setFileSize(file.getSize());
        doc.setFileType(FileUtil.extName(file.getOriginalFilename()));
        doc.setFileHash(fileHash);
        if (duplicate) {
            // 同文件重复入库：创建记录但默认跳过向量化（SKIPPED），返回 null 由前端提醒
            doc.setVectorStatus(VectorStatus.SKIPPED.name());
            doc.setErrorMsg("与已有文档内容相同，默认未入库；可点击强制入库");
            knowledgeDocMapper.insert(doc);
            log.info("检测到重复文件，跳过向量化: knowledgeId={}, name={}", knowledgeId, file.getOriginalFilename());
            return null;
        }
        // 用户设置"默认不入库"：创建记录但不向量化（REMOVED），可手动重新入库
        Integer defaultVectorize = UserContext.getUser().getDefaultVectorize();
        if (defaultVectorize != null && defaultVectorize == 0) {
            doc.setVectorStatus(VectorStatus.REMOVED.name());
            doc.setErrorMsg("已设置默认不入库，可点击重新入库");
            knowledgeDocMapper.insert(doc);
            log.info("默认不入库设置生效: knowledgeId={}, name={}", knowledgeId, file.getOriginalFilename());
            return null;
        }
        doc.setVectorStatus(VectorStatus.PENDING.name());
        knowledgeDocMapper.insert(doc);
        // 长任务走 Redis 消息队列：Java 提交任务（状态 PENDING + 入队），Python worker 消费执行，
        // 完成后回调 Java 回写状态；前端轮询 /task/{taskId}，规避向量化耗时导致的 HTTP 超时
        return taskService.publishVectorize(knowledgeId, doc.getId(), fileUrl, doc.getName());
    }

    @Override
    public void removeVector(Long knowledgeId, Long docId) {
        KnowledgeDoc doc = getDocInKnowledge(knowledgeId, docId);
        if (!VectorStatus.SUCCESS.name().equals(doc.getVectorStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档未入库，无需移除");
        }
        // 删除该文档向量（Python Agent），降级容忍
        deleteVectorsBestEffort(knowledgeId, docId);
        markRemoved(docId);
    }

    @Override
    public void deleteDoc(Long knowledgeId, Long docId) {
        getDocInKnowledge(knowledgeId, docId);
        // 用户删除：记录删除来源为 user（用户可自恢复）
        knowledgeDocMapper.markDeleted(docId, "user");
        deleteVectorsBestEffort(knowledgeId, docId);
        log.info("删除文档: knowledgeId={}, docId={}", knowledgeId, docId);
    }

    @Override
    public void purgeDoc(Long knowledgeId, Long docId) {
        getOwnedKnowledge(knowledgeId);
        // selectAnyById 绕过 @TableLogic，普通与已删除文档均可彻底删除（selectById 查不到已删除文档）
        KnowledgeDoc doc = knowledgeDocMapper.selectAnyById(docId);
        if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        // 彻底删除 = 逻辑删除 + 标记 purged：前端「已删除」列表不再展示，用户不可自恢复。
        // 本地文件保留（逻辑删除，管理员可查、文件无需重建）；向量一并清除避免被检索。
        deleteVectorsBestEffort(knowledgeId, docId);
        knowledgeDocMapper.markPurged(docId);
        log.info("彻底删除文档: knowledgeId={}, docId={}", knowledgeId, docId);
    }

    @Override
    public String restoreDoc(Long knowledgeId, Long docId) {
        getOwnedKnowledge(knowledgeId);
        KnowledgeDoc doc = knowledgeDocMapper.selectAnyById(docId);
        if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (doc.getIsDelete() == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档未被删除");
        }
        // 管理员删除的文档用户不可恢复
        if ("admin".equals(doc.getDeleteSource())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该文件已被管理员删除，无法恢复");
        }
        // 用户彻底删除（purged）的文档不可自恢复
        if ("purged".equals(doc.getDeleteSource())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该文件已被彻底删除，无法恢复");
        }
        // 文件哈希被管理员封禁（如其他用户同内容文件被删）→ 同样不可恢复
        if (StrUtil.isNotBlank(doc.getFileHash()) && forbiddenFileHashMapper.existsByHash(doc.getFileHash())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该文件已被管理员删除，无法恢复");
        }
        int rows = knowledgeDocMapper.restoreDeleted(docId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在或未被删除");
        }
        // 恢复后重新入库（删除时向量已清除），并发布向量化任务
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(docId);
        update.setVectorStatus(VectorStatus.PENDING.name());
        update.setErrorMsg(null);
        knowledgeDocMapper.updateById(update);
        return taskService.publishVectorize(knowledgeId, doc.getId(), doc.getFileUrl(), doc.getName());
    }

    @Override
    public int batchRemoveVector(Long knowledgeId, List<Long> docIds) {
        getOwnedKnowledge(knowledgeId);
        if (docIds == null || docIds.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (Long docId : docIds) {
            KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
            if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
                continue;
            }
            if (!VectorStatus.SUCCESS.name().equals(doc.getVectorStatus())) {
                continue;
            }
            deleteVectorsBestEffort(knowledgeId, docId);
            markRemoved(docId);
            removed++;
        }
        log.info("批量移除入库: knowledgeId={}, count={}", knowledgeId, removed);
        return removed;
    }

    @Override
    public int batchDeleteDocs(Long knowledgeId, List<Long> docIds) {
        getOwnedKnowledge(knowledgeId);
        if (docIds == null || docIds.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (Long docId : docIds) {
            KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
            if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
                continue;
            }
            // 用户批量删除：记录删除来源为 user
            knowledgeDocMapper.markDeleted(docId, "user");
            deleteVectorsBestEffort(knowledgeId, docId);
            deleted++;
        }
        log.info("批量删除文档: knowledgeId={}, count={}", knowledgeId, deleted);
        return deleted;
    }

    @Override
    public List<String> batchVectorize(Long knowledgeId, List<Long> docIds) {
        getOwnedKnowledge(knowledgeId);
        if (docIds == null || docIds.isEmpty()) {
            return List.of();
        }
        List<String> taskIds = new ArrayList<>();
        for (Long docId : docIds) {
            // selectById 过滤已删除文档；已入库的跳过，其余（未入库/失败/重复等）提交向量化任务
            KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
            if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
                continue;
            }
            if (VectorStatus.SUCCESS.name().equals(doc.getVectorStatus())) {
                continue;
            }
            taskIds.add(taskService.publishVectorize(knowledgeId, doc.getId(), doc.getFileUrl(), doc.getName()));
        }
        log.info("批量入库: knowledgeId={}, 提交任务数={}", knowledgeId, taskIds.size());
        return taskIds;
    }

    /**
     * 查询文档且校验属于该知识库
     */
    private KnowledgeDoc getDocInKnowledge(Long knowledgeId, Long docId) {
        getOwnedKnowledge(knowledgeId);
        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    /**
     * 调 Python 删该文档向量，失败降级容忍
     */
    private void deleteVectorsBestEffort(Long knowledgeId, Long docId) {
        try {
            pythonAgentClient.deleteKnowledge(new DeleteVectorRequest(String.valueOf(knowledgeId), String.valueOf(docId)));
            log.info("文档向量删除成功: knowledgeId={}, docId={}", knowledgeId, docId);
        } catch (Exception e) {
            log.warn("文档向量删除失败（已降级）: knowledgeId={}, docId={}, error={}", knowledgeId, docId, e.getMessage());
        }
    }

    /**
     * 将文档状态置为未入库（REMOVED）
     */
    private void markRemoved(Long docId) {
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(docId);
        update.setVectorStatus(VectorStatus.REMOVED.name());
        update.setErrorMsg("已从索引移除，可点击重新入库");
        knowledgeDocMapper.updateById(update);
    }

    @Override
    public String reVectorizeDoc(Long knowledgeId, Long docId) {
        getOwnedKnowledge(knowledgeId);
        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (VectorStatus.SUCCESS.name().equals(doc.getVectorStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档已入库，无需重新入库");
        }
        // 供「强制入库」(SKIPPED 重复文件) 与「重新入库」(FAILED/PENDING) 复用：重新发布向量化任务
        return taskService.publishVectorize(knowledgeId, doc.getId(), doc.getFileUrl(), doc.getName());
    }

    /**
     * 计算文件内容 SHA-256（失败抛系统异常）
     */
    private String computeFileHash(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return DigestUtil.sha256Hex(in);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文件内容失败");
        }
    }

    @Override
    public List<KnowledgeDocVO> listDocs(Long knowledgeId, Integer deleted, String category) {
        getOwnedKnowledge(knowledgeId);
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectDocsByFilter(knowledgeId, deleted, category);
        return docs.stream().map(KnowledgeDocVO::from).toList();
    }

    @Override
    public UserStatsVO getUserStats(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 id 不能为空");
        }
        // 工具回调场景无登录态，必须按 userId 精确过滤，防止越权查他人数据
        List<Knowledge> knowledgeList = knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getUserId, userId));
        List<Long> knowledgeIds = knowledgeList.stream().map(Knowledge::getId).toList();

        UserStatsVO vo = new UserStatsVO();
        vo.setUserId(userId);
        vo.setKnowledgeCount((long) knowledgeIds.size());
        if (knowledgeIds.isEmpty()) {
            vo.setDocCount(0L);
            vo.setVectorSuccess(0L);
            vo.setVectorPending(0L);
            vo.setVectorFailed(0L);
            return vo;
        }
        LambdaQueryWrapper<KnowledgeDoc> inDocs = new LambdaQueryWrapper<KnowledgeDoc>()
                .in(KnowledgeDoc::getKnowledgeId, knowledgeIds);
        vo.setDocCount(knowledgeDocMapper.selectCount(inDocs));
        vo.setVectorSuccess(knowledgeDocMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .in(KnowledgeDoc::getKnowledgeId, knowledgeIds)
                .eq(KnowledgeDoc::getVectorStatus, VectorStatus.SUCCESS.name())));
        vo.setVectorPending(knowledgeDocMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .in(KnowledgeDoc::getKnowledgeId, knowledgeIds)
                .eq(KnowledgeDoc::getVectorStatus, VectorStatus.PENDING.name())));
        vo.setVectorFailed(knowledgeDocMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .in(KnowledgeDoc::getKnowledgeId, knowledgeIds)
                .eq(KnowledgeDoc::getVectorStatus, VectorStatus.FAILED.name())));
        // 最近上传时间：取该用户最近一条文档的创建时间
        KnowledgeDoc latest = knowledgeDocMapper.selectOne(new LambdaQueryWrapper<KnowledgeDoc>()
                .in(KnowledgeDoc::getKnowledgeId, knowledgeIds)
                .orderByDesc(KnowledgeDoc::getCreateTime)
                .last("LIMIT 1"));
        if (latest != null) {
            vo.setLastUploadTime(latest.getCreateTime());
        }
        return vo;
    }
}
