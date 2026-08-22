package com.agent.rag.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.VectorStatus;
import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.req.VectorizeRequest;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.UserStatsVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.agent.rag.service.KnowledgeService;
import com.agent.rag.storage.FileStorageService;
import com.agent.rag.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.Executor;

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

    @Resource(name = "vectorizeExecutor")
    private Executor vectorizeExecutor;

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
    public Long uploadDoc(Long knowledgeId, MultipartFile file) {
        getOwnedKnowledge(knowledgeId);
        String fileUrl = fileStorageService.store(file, UserContext.getUser().getId());
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setKnowledgeId(knowledgeId);
        doc.setName(file.getOriginalFilename());
        doc.setFileUrl(fileUrl);
        doc.setFileSize(file.getSize());
        doc.setFileType(FileUtil.extName(file.getOriginalFilename()));
        doc.setVectorStatus(VectorStatus.PENDING.name());
        knowledgeDocMapper.insert(doc);
        // 异步向量化：与上传解耦，失败不影响文档入库，状态可查
        Long docId = doc.getId();
        vectorizeExecutor.execute(() -> doVectorize(docId, knowledgeId, fileUrl, doc.getName()));
        return docId;
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

    /**
     * 调用 Python Agent 执行向量化，并更新文档状态
     */
    private void doVectorize(Long docId, Long knowledgeId, String fileUrl, String name) {
        try {
            pythonAgentClient.vectorize(new VectorizeRequest(knowledgeId, docId, fileUrl, name));
            updateVectorStatus(docId, VectorStatus.SUCCESS, null);
            log.info("文档向量化成功: docId={}", docId);
        } catch (Exception e) {
            log.error("文档向量化失败: docId={}, knowledgeId={}", docId, knowledgeId, e);
            String errorMsg = StrUtil.sub(e.getMessage(), 0, 500);
            updateVectorStatus(docId, VectorStatus.FAILED, errorMsg);
        }
    }

    private void updateVectorStatus(Long docId, VectorStatus status, String errorMsg) {
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(docId);
        update.setVectorStatus(status.name());
        update.setErrorMsg(errorMsg);
        knowledgeDocMapper.updateById(update);
    }
}
