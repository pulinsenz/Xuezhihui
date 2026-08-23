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
     * 删除单个文档：逻辑删除（来源=用户）+ 删除该文档向量，用户可自恢复
     */
    void deleteDoc(Long knowledgeId, Long docId);

    /**
     * 恢复用户自己删除的文档：仅 user 来源可恢复，管理员删除的拒绝；恢复后重新入库
     *
     * @return 重新入库的任务 id
     */
    String restoreDoc(Long knowledgeId, Long docId);

    /**
     * 批量移除入库：删除所选文档向量（保留文档记录），返回成功处理的文档数
     */
    int batchRemoveVector(Long knowledgeId, List<Long> docIds);

    /**
     * 批量删除文档：逻辑删除（来源=用户）+ 删除各文档向量，返回成功删除的文档数
     */
    int batchDeleteDocs(Long knowledgeId, List<Long> docIds);

    /**
     * 知识库文档列表（deleted 过滤：null=全部/0=正常/1=已删除）
     */
    List<KnowledgeDocVO> listDocs(Long knowledgeId, Integer deleted);

    /**
     * 用户业务数据统计（工具 Agent 回调），跨用户时按 userId 精确过滤
     */
    UserStatsVO getUserStats(Long userId);
}
