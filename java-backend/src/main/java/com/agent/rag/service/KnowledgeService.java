package com.agent.rag.service;

import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.UserStatsVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.entity.Knowledge;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库服务
 *
 * @author pulinsenz
 */
public interface KnowledgeService {

    /**
     * 创建知识库
     */
    Long createKnowledge(KnowledgeCreateRequest request);

    /**
     * 我的知识库列表（含文档数）
     */
    List<KnowledgeVO> listMyKnowledge();

    /**
     * 获取本人知识库（校验所属人，不存在或无权抛异常）
     */
    Knowledge getOwnedKnowledge(Long knowledgeId);

    /**
     * 删除知识库及其文档
     */
    void deleteKnowledge(Long knowledgeId);

    /**
     * 上传文档：存文件 + 记元数据 + 提交向量化任务（Redis 消息队列），返回任务 id 供前端轮询。
     * 同知识库内存在相同内容文件时：创建记录但跳过向量化（SKIPPED），返回 null 提示重复。
     */
    String uploadDoc(Long knowledgeId, MultipartFile file);

    /**
     * 文档强制/重新入库（SKIPPED 重复文件强制向量化、FAILED/PENDING/REMOVED 重试），返回任务 id
     */
    String reVectorizeDoc(Long knowledgeId, Long docId);

    /**
     * 移除入库：删除文档向量（保留文档记录），状态置为 REMOVED（未入库）
     */
    void removeVector(Long knowledgeId, Long docId);

    /**
     * 知识库文档列表
     */
    List<KnowledgeDocVO> listDocs(Long knowledgeId);

    /**
     * 用户业务数据统计（工具 Agent 回调），跨用户时按 userId 精确过滤
     */
    UserStatsVO getUserStats(Long userId);
}
