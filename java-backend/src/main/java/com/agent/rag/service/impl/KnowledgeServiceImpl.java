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
        getOwnedKnowledge(knowledgeId);
        KnowledgeDoc doc = knowledgeDocMapper.selectById(docId);
        if (doc == null || !doc.getKnowledgeId().equals(knowledgeId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        if (!VectorStatus.SUCCESS.name().equals(doc.getVectorStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文档未入库，无需移除");
        }
        // 删除该文档向量（Python Agent），降级容忍
        try {
            pythonAgentClient.deleteKnowledge(new DeleteVectorRequest(String.valueOf(knowledgeId), String.valueOf(docId)));
            log.info("文档向量移除成功: knowledgeId={}, docId={}", knowledgeId, docId);
        } catch (Exception e) {
            log.warn("文档向量移除失败（已降级）: knowledgeId={}, docId={}, error={}", knowledgeId, docId, e.getMessage());
        }
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
    public List<KnowledgeDocVO> listDocs(Long knowledgeId) {
        getOwnedKnowledge(knowledgeId);
        List<KnowledgeDoc> docs = knowledgeDocMapper.selectList(new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(KnowledgeDoc::getKnowledgeId, knowledgeId)
                .orderByDesc(KnowledgeDoc::getCreateTime));
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
