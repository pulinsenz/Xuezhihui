package com.agent.rag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.RoleConstant;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.req.UpdateUserRoleRequest;
import com.agent.rag.dto.req.UserQueryRequest;
import com.agent.rag.dto.resp.AdminUserVO;
import com.agent.rag.dto.resp.KnowledgeDocVO;
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
import com.agent.rag.service.AdminService;
import com.agent.rag.service.TaskService;
import com.agent.rag.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理员服务实现
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    @Resource
    private UserMapper userMapper;

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
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JwtProperties jwtProperties;

    @Resource
    private PythonAgentClient pythonAgentClient;

    @Resource
    private TaskService taskService;

    @Override
    public Page<AdminUserVO> listUsers(UserQueryRequest request) {
        long pageNum = normalizePageNum(request);
        long pageSize = normalizePageSize(request);
        // 手写 SQL 分页：deleted 可查已删除用户（BaseMapper 会强制过滤 isDelete=0）
        IPage<User> userPage = userMapper.selectUserPage(
                new Page<>(pageNum, pageSize),
                request.getKeyword(),
                request.getDeleted());
        List<AdminUserVO> records = userPage.getRecords().stream()
                .map(AdminUserVO::from)
                .toList();
        Page<AdminUserVO> voPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public void updateUserRole(Long userId, UpdateUserRoleRequest request) {
        if (userId == null || request == null || StrUtil.isBlank(request.getUserRole())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        String role = request.getUserRole();
        if (!RoleConstant.USER.equals(role) && !RoleConstant.ADMIN.equals(role)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色只能是 user 或 admin");
        }
        // 不能操作自己，防止误降级管理员
        if (userId.equals(UserContext.getUser().getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能修改自己的角色");
        }
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        User update = new User();
        update.setId(userId);
        update.setUserRole(role);
        userMapper.updateById(update);
        log.info("修改角色成功: userId={}, role={}", userId, role);
    }

    @Override
    public void deleteUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        if (userId.equals(UserContext.getUser().getId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能删除自己");
        }
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_EXIST);
        }
        userMapper.deleteById(userId);
        // 清理该用户全部登录 token，强制下线
        evictLoginTokens(userId);
        log.info("删除用户成功: userId={}", userId);
    }

    @Override
    public void restoreUser(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }
        int rows = userMapper.restoreDeleted(userId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在或未被删除");
        }
        log.info("恢复用户成功: userId={}", userId);
    }

    @Override
    public Page<KnowledgeVO> listAllKnowledge(UserQueryRequest request) {
        long pageNum = normalizePageNum(request);
        long pageSize = normalizePageSize(request);
        // 手写 SQL 分页：deleted 可查已删除知识库（BaseMapper 会强制过滤 isDelete=0）
        IPage<Knowledge> knowledgePage = knowledgeMapper.selectKnowledgePage(
                new Page<>(pageNum, pageSize),
                request.getKeyword(),
                request.getDeleted());
        List<KnowledgeVO> records = knowledgePage.getRecords().stream().map(knowledge -> {
            KnowledgeVO vo = KnowledgeVO.from(knowledge);
            // 含已删除文档计数，管理员恢复时可预览将重新入库的文档数
            vo.setDocCount(knowledgeDocMapper.countAll(knowledge.getId()));
            return vo;
        }).toList();
        Page<KnowledgeVO> voPage = new Page<>(knowledgePage.getCurrent(), knowledgePage.getSize(), knowledgePage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public KnowledgeVO getKnowledgeDetail(Long knowledgeId) {
        Knowledge knowledge = getKnowledgeAny(knowledgeId);
        KnowledgeVO vo = KnowledgeVO.from(knowledge);
        vo.setDocCount(knowledgeDocMapper.countAll(knowledgeId));
        return vo;
    }

    @Override
    public Page<KnowledgeDocVO> listAllDocs(Long knowledgeId, UserQueryRequest request) {
        getKnowledgeAny(knowledgeId);
        long pageNum = normalizePageNum(request);
        long pageSize = normalizePageSize(request);
        IPage<KnowledgeDoc> docPage = knowledgeDocMapper.selectDocPage(
                new Page<>(pageNum, pageSize),
                knowledgeId,
                request.getKeyword(),
                request.getDeleted());
        List<KnowledgeDocVO> records = docPage.getRecords().stream().map(KnowledgeDocVO::from).toList();
        Page<KnowledgeDocVO> voPage = new Page<>(docPage.getCurrent(), docPage.getSize(), docPage.getTotal());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    @Transactional
    public void deleteKnowledgeByAdmin(Long knowledgeId) {
        getKnowledgeAny(knowledgeId);
        // 逻辑删除知识库 + 全部当前正常文档（来源=admin，用户不可恢复该文件）
        knowledgeMapper.deleteById(knowledgeId);
        knowledgeDocMapper.markDeletedByKnowledgeId(knowledgeId, "admin");
        // 级联清理收藏与协作者关系，避免残留
        knowledgeFavoriteMapper.delete(new LambdaQueryWrapper<KnowledgeFavorite>()
                .eq(KnowledgeFavorite::getKnowledgeId, knowledgeId));
        knowledgeMemberMapper.delete(new LambdaQueryWrapper<KnowledgeMember>()
                .eq(KnowledgeMember::getKnowledgeId, knowledgeId));
        // 清理向量库（Python Agent），降级：失败不影响元数据删除
        deleteVectorsBestEffort(String.valueOf(knowledgeId), null);
        log.info("管理员删除知识库: knowledgeId={}", knowledgeId);
    }

    @Override
    @Transactional
    public List<String> restoreKnowledge(Long knowledgeId) {
        Knowledge knowledge = getKnowledgeAny(knowledgeId);
        if (knowledge.getIsDelete() == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库未被删除");
        }
        int rows = knowledgeMapper.restoreDeleted(knowledgeId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在或未被删除");
        }
        // 逐条恢复文档，跳过被管理员封禁哈希的文档：其本地文件已被级联删除，
        // 若批量恢复会生成指向缺失文件的活跃记录，重新向量化必然失败
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectAllByKnowledgeId(knowledgeId).stream()
                .filter(doc -> StrUtil.isBlank(doc.getFileHash()) || !forbiddenFileHashMapper.existsByHash(doc.getFileHash()))
                .toList();
        docs.forEach(doc -> knowledgeDocMapper.restoreDeleted(doc.getId()));
        List<String> taskIds = docs.stream()
                .map(doc -> taskService.publishVectorize(knowledgeId, doc.getId(), doc.getFileUrl(), doc.getName()))
                .toList();
        log.info("管理员恢复知识库: knowledgeId={}, 重新入库文档数={}", knowledgeId, taskIds.size());
        return taskIds;
    }

    @Override
    @Transactional
    public void deleteDocByAdmin(Long knowledgeId, Long docId) {
        KnowledgeDoc doc = getDocAny(knowledgeId, docId);
        // getDocAny 用 selectAnyById 会命中已删除文档，此处拦截防止静默 no-op
        if (doc.getIsDelete() != null && doc.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档已删除");
        }
        // 记录文件哈希到黑名单，并级联删除所有同内容文档（跨用户/知识库）
        banAndCascade(doc);
        log.info("管理员删除文档: knowledgeId={}, docId={}", knowledgeId, docId);
    }

    @Override
    @Transactional
    public String restoreDoc(Long knowledgeId, Long docId) {
        KnowledgeDoc doc = getDocAny(knowledgeId, docId);
        if (doc.getIsDelete() == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档未被删除");
        }
        // 被管理员封禁哈希的文档不可恢复（本地文件已被级联删除）
        if (StrUtil.isNotBlank(doc.getFileHash()) && forbiddenFileHashMapper.existsByHash(doc.getFileHash())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该文件已被管理员删除，无法恢复");
        }
        int rows = knowledgeDocMapper.restoreDeleted(docId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在或未被删除");
        }
        return taskService.publishVectorize(knowledgeId, doc.getId(), doc.getFileUrl(), doc.getName());
    }

    @Override
    public String reVectorize(Long knowledgeId, Long docId) {
        KnowledgeDoc doc = getDocAny(knowledgeId, docId);
        if (doc.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档已删除，请先恢复");
        }
        if (!"FAILED".equals(doc.getVectorStatus()) && !"PENDING".equals(doc.getVectorStatus())
                && !"SKIPPED".equals(doc.getVectorStatus()) && !"REMOVED".equals(doc.getVectorStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持对未入库或失败的文档重新入库");
        }
        return taskService.publishVectorize(knowledgeId, doc.getId(), doc.getFileUrl(), doc.getName());
    }

    @Override
    public void removeDocVector(Long knowledgeId, Long docId) {
        KnowledgeDoc doc = getDocAny(knowledgeId, docId);
        if (!"SUCCESS".equals(doc.getVectorStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档未入库，无需移除");
        }
        deleteVectorsBestEffort(String.valueOf(knowledgeId), String.valueOf(docId));
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(docId);
        update.setVectorStatus("REMOVED");
        update.setErrorMsg("已从索引移除，可点击重新入库");
        knowledgeDocMapper.updateById(update);
        log.info("管理员移除文档入库: knowledgeId={}, docId={}", knowledgeId, docId);
    }

    @Override
    public int batchRemoveDocVector(Long knowledgeId, List<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (Long docId : docIds) {
            KnowledgeDoc doc = knowledgeDocMapper.selectAnyById(docId);
            if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
                continue;
            }
            if (!"SUCCESS".equals(doc.getVectorStatus())) {
                continue;
            }
            deleteVectorsBestEffort(String.valueOf(knowledgeId), String.valueOf(docId));
            KnowledgeDoc update = new KnowledgeDoc();
            update.setId(docId);
            update.setVectorStatus("REMOVED");
            update.setErrorMsg("已从索引移除，可点击重新入库");
            knowledgeDocMapper.updateById(update);
            removed++;
        }
        log.info("管理员批量移除入库: knowledgeId={}, count={}", knowledgeId, removed);
        return removed;
    }

    @Override
    public int batchDeleteDocs(Long knowledgeId, List<Long> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return 0;
        }
        Set<String> processedHashes = new HashSet<>();
        int deleted = 0;
        for (Long docId : docIds) {
            KnowledgeDoc doc = knowledgeDocMapper.selectAnyById(docId);
            if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
                continue;
            }
            if (doc.getIsDelete() != null && doc.getIsDelete() == 1) {
                continue;
            }
            // 同内容哈希已在本次批量中级联处理过（会一并删除本记录），避免重复封禁
            if (StrUtil.isNotBlank(doc.getFileHash()) && !processedHashes.add(doc.getFileHash())) {
                continue;
            }
            banAndCascade(doc);
            deleted++;
        }
        log.info("管理员批量删除文档: knowledgeId={}, count={}", knowledgeId, deleted);
        return deleted;
    }

    /**
     * 管理员删除文档核心：封禁文件哈希 + 级联删除所有同内容正常文档。
     * <p>
     * 1. 有哈希：写入黑名单表（全局禁止上传/恢复）→ 查全部同哈希正常文档（含目标，天然去重）
     *    → 逐条逻辑删除（来源=admin）+ 删向量。
     * 2. 无哈希（历史数据）：无法全局去重，退回仅删目标文档。
     * 本地文件保留（逻辑删除，不随删除清理）。DB 写在事务内保证原子，Python 向量删除失败不阻断。
     */
    private void banAndCascade(KnowledgeDoc target) {
        if (StrUtil.isBlank(target.getFileHash())) {
            knowledgeDocMapper.markDeleted(target.getId(), "admin");
            deleteVectorsBestEffort(String.valueOf(target.getKnowledgeId()), String.valueOf(target.getId()));
            return;
        }
        forbiddenFileHashMapper.insertIgnore(target.getFileHash());
        List<KnowledgeDoc> sameHashDocs = knowledgeDocMapper.selectActiveByHash(target.getFileHash());
        for (KnowledgeDoc doc : sameHashDocs) {
            knowledgeDocMapper.markDeleted(doc.getId(), "admin");
            // 必须用每条文档自己的 knowledgeId/docId：Python 按 (knowledge_id, doc_id) 删向量，
            // 用目标库 id 会留下跨库孤儿向量
            deleteVectorsBestEffort(String.valueOf(doc.getKnowledgeId()), String.valueOf(doc.getId()));
        }
    }

    /**
     * 查询知识库（不区分删除状态，管理员用）
     */
    private Knowledge getKnowledgeAny(Long knowledgeId) {
        Knowledge knowledge = knowledgeMapper.selectAnyById(knowledgeId);
        if (knowledge == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return knowledge;
    }

    /**
     * 查询文档（不区分删除状态，且校验归属知识库）
     */
    private KnowledgeDoc getDocAny(Long knowledgeId, Long docId) {
        if (docId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档 id 不能为空");
        }
        KnowledgeDoc doc = knowledgeDocMapper.selectAnyById(docId);
        if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return doc;
    }

    /**
     * 调 Python 删向量（按 docId 或整库），失败降级不影响主流程
     */
    private void deleteVectorsBestEffort(String knowledgeId, String docId) {
        try {
            pythonAgentClient.deleteKnowledge(new DeleteVectorRequest(knowledgeId, docId));
            log.info("向量库清理成功: knowledgeId={}, docId={}", knowledgeId, docId);
        } catch (Exception e) {
            log.warn("向量库清理失败（已降级）: knowledgeId={}, docId={}, error={}", knowledgeId, docId, e.getMessage());
        }
    }

    private long normalizePageNum(UserQueryRequest request) {
        Long pageNum = request.getPageNum();
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private long normalizePageSize(UserQueryRequest request) {
        Long pageSize = request.getPageSize();
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    /**
     * 用 SCAN 遍历白名单，删除指定用户的全部 token（避免 KEYS 阻塞 Redis）
     */
    private void evictLoginTokens(Long userId) {
        String prefix = jwtProperties.getRedisPrefix();
        String userIdStr = String.valueOf(userId);
        long removed = 0;
        Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(100).build());
        try (cursor) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                if (userIdStr.equals(stringRedisTemplate.opsForValue().get(key))) {
                    stringRedisTemplate.delete(key);
                    removed++;
                }
            }
        }
        log.info("清理用户登录 token: userId={}, removed={}", userId, removed);
    }
}
