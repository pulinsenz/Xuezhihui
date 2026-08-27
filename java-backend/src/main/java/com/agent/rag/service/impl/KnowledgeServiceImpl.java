package com.agent.rag.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.Result;
import com.agent.rag.common.VectorStatus;
import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.req.KnowledgeUpdateRequest;
import com.agent.rag.dto.req.MemberInviteRequest;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.MemberVO;
import com.agent.rag.dto.resp.UserStatsVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.KnowledgeFavorite;
import com.agent.rag.entity.KnowledgeMember;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeFavoriteMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.agent.rag.mapper.KnowledgeMemberMapper;
import com.agent.rag.mapper.ForbiddenFileHashMapper;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.KnowledgeService;
import com.agent.rag.service.TaskService;
import com.agent.rag.storage.FileStorageService;
import com.agent.rag.util.UserContext;
import com.agent.rag.util.UploadFileTypeValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private KnowledgeFavoriteMapper knowledgeFavoriteMapper;

    @Resource
    private KnowledgeMemberMapper knowledgeMemberMapper;

    @Resource
    private ForbiddenFileHashMapper forbiddenFileHashMapper;

    @Resource
    private UserMapper userMapper;

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
        // 默认私有；isPublic=1 则公开供他人浏览/收藏/复制
        knowledge.setIsPublic(request.getIsPublic() != null && request.getIsPublic() == 1 ? 1 : 0);
        knowledgeMapper.insert(knowledge);
        log.info("创建知识库成功: knowledgeId={}, userId={}, isPublic={}",
                knowledge.getId(), knowledge.getUserId(), knowledge.getIsPublic());
        return knowledge.getId();
    }

    @Override
    public void updateKnowledge(KnowledgeUpdateRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库 id 不能为空");
        }
        if (StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库名称不能为空");
        }
        // 仅作者可编辑知识库信息
        getOwnedKnowledge(request.getId());
        Knowledge update = new Knowledge();
        update.setId(request.getId());
        update.setName(request.getName().trim());
        update.setDescription(request.getDescription());
        update.setCover(request.getCover());
        // updateById 跳过 null 字段，未传则保持原值
        if (request.getIsPublic() != null) {
            update.setIsPublic(request.getIsPublic());
        }
        knowledgeMapper.updateById(update);
        // 公开→私有：已有收藏仍保留访问（viewable 兜底），仅从公开列表移除
        log.info("更新知识库信息: knowledgeId={}, isPublic={}", request.getId(), request.getIsPublic());
    }

    @Override
    public List<KnowledgeVO> listMyKnowledge() {
        Long userId = UserContext.getUser().getId();
        // 我拥有的知识库
        List<Knowledge> owned = knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getUserId, userId));
        // 我收藏的知识库（源库被删除时 selectBatchIds 自动过滤）
        Set<Long> favIds = myFavoriteIds(userId);
        List<Knowledge> favorited = favIds.isEmpty() ? List.of()
                : knowledgeMapper.selectBatchIds(favIds);
        // 合并去重（收藏不能是本人库，理论无重叠，按 id 去重兜底）
        Map<Long, Knowledge> merged = new LinkedHashMap<>();
        owned.forEach(k -> merged.put(k.getId(), k));
        favorited.forEach(k -> merged.putIfAbsent(k.getId(), k));
        List<Knowledge> all = new ArrayList<>(merged.values());
        // createTime 理论上不为空（DB 默认），null 时排最后，避免排序 NPE
        all.sort(Comparator.comparing(Knowledge::getCreateTime,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        // isOwner 必须按作者判断：列表含「我收藏的他人公开库」，硬编码 true 会让收藏的库也显示"我的"
        return all.stream().map(k -> enrichVO(k, userId, k.getUserId().equals(userId),
                favIds.contains(k.getId()))).toList();
    }

    @Override
    public List<KnowledgeVO> listPublicKnowledge(String keyword) {
        Long userId = UserContext.getUser().getId();
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getIsPublic, 1)
                .orderByDesc(Knowledge::getCreateTime);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Knowledge::getName, keyword.trim());
        }
        List<Knowledge> list = knowledgeMapper.selectList(wrapper);
        Set<Long> favIds = myFavoriteIds(userId);
        return list.stream().map(k -> {
            boolean isOwner = k.getUserId().equals(userId);
            return enrichVO(k, userId, isOwner, favIds.contains(k.getId()));
        }).toList();
    }

    @Override
    public Knowledge getOwnedKnowledge(Long knowledgeId) {
        Knowledge knowledge = getKnowledgeOrThrow(knowledgeId);
        if (!knowledge.getUserId().equals(UserContext.getUser().getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权操作该知识库");
        }
        return knowledge;
    }

    @Override
    public Knowledge getManageableKnowledge(Long knowledgeId) {
        Knowledge knowledge = getKnowledgeOrThrow(knowledgeId);
        Long userId = UserContext.getUser().getId();
        if (!knowledge.getUserId().equals(userId) && !isMember(knowledgeId, userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权操作该知识库");
        }
        return knowledge;
    }

    @Override
    public Knowledge getViewableKnowledge(Long knowledgeId) {
        Knowledge knowledge = getKnowledgeOrThrow(knowledgeId);
        Long userId = UserContext.getUser().getId();
        boolean owner = knowledge.getUserId().equals(userId);
        if (owner || isMember(knowledgeId, userId) || isFavorited(knowledgeId, userId)) {
            return knowledge;
        }
        // 公开知识库：任何人可查看
        if (knowledge.getIsPublic() != null && knowledge.getIsPublic() == 1) {
            return knowledge;
        }
        throw new BusinessException(ErrorCode.NO_AUTH, "无权访问该知识库");
    }

    @Override
    public KnowledgeDoc getViewableDoc(Long knowledgeId, Long docId) {
        // 文档查看门槛与知识库一致：作者/协作者/收藏者/公开库可访问
        getViewableKnowledge(knowledgeId);
        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    @Override
    public List<String> listDocChunks(Long knowledgeId, Long docId) {
        KnowledgeDoc doc = getViewableDoc(knowledgeId, docId);
        // 未入库的文档没有切片，直接返回空，避免无谓调用 Python
        if (!VectorStatus.SUCCESS.name().equals(doc.getVectorStatus())) {
            return List.of();
        }
        try {
            Result<List<String>> resp = pythonAgentClient.docChunks(
                    String.valueOf(knowledgeId), String.valueOf(docId));
            if (resp == null || resp.getCode() != 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR,
                        "切片查询失败: " + (resp == null ? "无响应" : resp.getMessage()));
            }
            return resp.getData() == null ? List.of() : resp.getData();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("切片查询异常: knowledgeId={}, docId={}", knowledgeId, docId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "切片服务不可用，请稍后重试");
        }
    }

    @Override
    public KnowledgeVO getKnowledgeDetail(Long knowledgeId) {
        Knowledge knowledge = getViewableKnowledge(knowledgeId);
        Long userId = UserContext.getUser().getId();
        boolean owner = knowledge.getUserId().equals(userId);
        boolean member = isMember(knowledgeId, userId);
        boolean favorite = isFavorited(knowledgeId, userId);
        // 浏览量：仅外部查看者（非作者/协作者）计数；校验通过后再自增，401 请求不计
        if (!owner && !member) {
            knowledgeMapper.incrementViewCount(knowledgeId);
        }
        return enrichVO(knowledge, userId, owner, favorite);
    }

    @Override
    public void deleteKnowledge(Long knowledgeId) {
        getOwnedKnowledge(knowledgeId);
        knowledgeMapper.deleteById(knowledgeId);
        knowledgeDocMapper.delete(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getKnowledgeId, knowledgeId));
        // 级联清理收藏与协作者关系，避免残留
        knowledgeFavoriteMapper.delete(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getKnowledgeId, knowledgeId));
        knowledgeMemberMapper.delete(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId));
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
        getManageableKnowledge(knowledgeId);
        // 类型白名单校验：与 python-agent 解析能力对齐，先于落盘/计算哈希
        UploadFileTypeValidator.checkDocument(file);
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
        getManageableKnowledge(knowledgeId);
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
        getManageableKnowledge(knowledgeId);
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
        getManageableKnowledge(knowledgeId);
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
        getManageableKnowledge(knowledgeId);
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
        getManageableKnowledge(knowledgeId);
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
        getManageableKnowledge(knowledgeId);
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
        getManageableKnowledge(knowledgeId);
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
        Knowledge knowledge = getViewableKnowledge(knowledgeId);
        Long userId = UserContext.getUser().getId();
        // 外部查看者（非作者/协作者）只读：强制只看正常文档，避免看到已删除记录
        boolean manageable = knowledge.getUserId().equals(userId) || isMember(knowledgeId, userId);
        // 注意：不能写 `manageable ? deleted : 0`，三元会把 Integer 拆箱成 int，deleted 为 null 时抛 NPE
        Integer effectiveDeleted;
        if (manageable) {
            effectiveDeleted = deleted;
        } else {
            effectiveDeleted = 0;
        }
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectDocsByFilter(knowledgeId, effectiveDeleted, category);
        return docs.stream().map(KnowledgeDocVO::from).toList();
    }

    @Override
    public void favorite(Long knowledgeId) {
        Knowledge knowledge = getKnowledgeOrThrow(knowledgeId);
        Long userId = UserContext.getUser().getId();
        if (knowledge.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能收藏自己的知识库");
        }
        if (knowledge.getIsPublic() == null || knowledge.getIsPublic() != 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只能收藏公开知识库");
        }
        int rows = knowledgeFavoriteMapper.insertIgnore(knowledgeId, userId);
        if (rows > 0) {
            knowledgeMapper.incrementFavoriteCount(knowledgeId);
            log.info("收藏知识库: knowledgeId={}, userId={}", knowledgeId, userId);
        }
    }

    @Override
    public void unfavorite(Long knowledgeId) {
        Long userId = UserContext.getUser().getId();
        int rows = knowledgeFavoriteMapper.deleteByKnowledgeAndUser(knowledgeId, userId);
        if (rows > 0) {
            knowledgeMapper.decrementFavoriteCount(knowledgeId);
            log.info("取消收藏知识库: knowledgeId={}, userId={}", knowledgeId, userId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyKnowledge(Long knowledgeId) {
        Knowledge source = getViewableKnowledge(knowledgeId);
        Long userId = UserContext.getUser().getId();
        // 复制者为新作者；默认私有，复制封面/简介
        Knowledge copy = new Knowledge();
        String baseName = StrUtil.blankToDefault(source.getName(), "未命名知识库");
        copy.setName(StrUtil.sub(baseName + "（副本）", 0, 128));
        copy.setDescription(source.getDescription());
        copy.setCover(source.getCover());
        copy.setUserId(userId);
        copy.setIsPublic(0);
        copy.setViewCount(0);
        copy.setFavoriteCount(0);
        knowledgeMapper.insert(copy);
        // 复制其下正常文档：复用文件，重新向量化索引到新知识库（worker 按 knowledgeId+docId 索引）
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectDocsByFilter(knowledgeId, 0, null);
        for (KnowledgeDoc doc : docs) {
            KnowledgeDoc nd = new KnowledgeDoc();
            nd.setKnowledgeId(copy.getId());
            nd.setName(doc.getName());
            nd.setFileUrl(doc.getFileUrl());
            nd.setFileSize(doc.getFileSize());
            nd.setFileType(doc.getFileType());
            nd.setFileHash(doc.getFileHash());
            nd.setVectorStatus(VectorStatus.PENDING.name());
            knowledgeDocMapper.insert(nd);
            taskService.publishVectorize(copy.getId(), nd.getId(), nd.getFileUrl(), nd.getName());
        }
        log.info("复制知识库完成: sourceId={}, newId={}, docs={}, userId={}",
                knowledgeId, copy.getId(), docs.size(), userId);
        return copy.getId();
    }

    @Override
    public String uploadCover(MultipartFile file) {
        // 图片白名单校验：排除 svg 等可执行脚本格式，防存储型 XSS
        UploadFileTypeValidator.checkImage(file);
        return fileStorageService.storeForWeb(file, UserContext.getUser().getId());
    }

    @Override
    public List<MemberVO> listMembers(Long knowledgeId) {
        getOwnedKnowledge(knowledgeId);
        List<KnowledgeMember> members = knowledgeMemberMapper.selectList(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId)
                .orderByAsc(KnowledgeMember::getCreateTime));
        List<MemberVO> vos = new ArrayList<>();
        for (KnowledgeMember member : members) {
            User user = userMapper.selectById(member.getUserId());
            if (user == null) {
                continue;
            }
            vos.add(MemberVO.from(user, member.getCreateTime()));
        }
        return vos;
    }

    @Override
    public void addMember(Long knowledgeId, MemberInviteRequest request) {
        getOwnedKnowledge(knowledgeId);
        if (request == null || (request.getUserId() == null && StrUtil.isBlank(request.getUserAccount()))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请提供目标用户 id 或账号");
        }
        User target;
        if (request.getUserId() != null) {
            target = userMapper.selectById(request.getUserId());
        } else {
            target = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUserAccount, request.getUserAccount().trim()));
        }
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        Long userId = UserContext.getUser().getId();
        if (target.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能邀请自己");
        }
        int rows = knowledgeMemberMapper.insertIgnore(knowledgeId, target.getId());
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该用户已是协作者");
        }
        log.info("邀请协作者成功: knowledgeId={}, userId={}", knowledgeId, target.getId());
    }

    @Override
    public void removeMember(Long knowledgeId, Long userId) {
        getOwnedKnowledge(knowledgeId);
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "协作者 id 不能为空");
        }
        int rows = knowledgeMemberMapper.deleteByKnowledgeAndUser(knowledgeId, userId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "该协作者不在知识库中");
        }
        log.info("移除协作者成功: knowledgeId={}, userId={}", knowledgeId, userId);
    }

    /**
     * 查询知识库（逻辑删除过滤），不存在抛异常
     */
    private Knowledge getKnowledgeOrThrow(Long knowledgeId) {
        if (knowledgeId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库 id 不能为空");
        }
        Knowledge knowledge = knowledgeMapper.selectById(knowledgeId);
        if (knowledge == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return knowledge;
    }

    /**
     * 当前用户是否为该知识库协作者
     */
    private boolean isMember(Long knowledgeId, Long userId) {
        return knowledgeMemberMapper.selectCount(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId)
                .eq(KnowledgeMember::getUserId, userId)) > 0;
    }

    /**
     * 当前用户是否已收藏该知识库
     */
    private boolean isFavorited(Long knowledgeId, Long userId) {
        return knowledgeFavoriteMapper.selectCount(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getKnowledgeId, knowledgeId)
                .eq(KnowledgeFavorite::getUserId, userId)) > 0;
    }

    /**
     * 当前用户收藏的知识库 id 集合
     */
    private Set<Long> myFavoriteIds(Long userId) {
        return knowledgeFavoriteMapper.selectList(new LambdaQueryWrapper<KnowledgeFavorite>()
                        .eq(KnowledgeFavorite::getUserId, userId))
                .stream().map(KnowledgeFavorite::getKnowledgeId).collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 富化 VO：docCount + 作者信息 + 权限/收藏标记
     */
    private KnowledgeVO enrichVO(Knowledge knowledge, Long currentUserId, boolean isOwner, boolean isFavorite) {
        KnowledgeVO vo = KnowledgeVO.from(knowledge);
        vo.setIsOwner(isOwner);
        vo.setIsFavorite(isFavorite);
        vo.setIsMember(isMember(knowledge.getId(), currentUserId));
        Long docCount = knowledgeDocMapper.selectCount(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getKnowledgeId, knowledge.getId()));
        vo.setDocCount(docCount);
        User author = userMapper.selectById(knowledge.getUserId());
        if (author != null) {
            vo.setAuthorName(StrUtil.blankToDefault(author.getUserName(), author.getUserAccount()));
            vo.setAuthorAvatar(author.getUserAvatar());
        } else {
            vo.setAuthorName("未知用户");
        }
        return vo;
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
