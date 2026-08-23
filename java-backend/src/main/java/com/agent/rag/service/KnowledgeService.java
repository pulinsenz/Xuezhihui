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
     * 上传文档：存文件 + 记元数据 + 提交向量化任务（Redis 消息队列），返回任务 id 供前端轮询
     */
    String uploadDoc(Long knowledgeId, MultipartFile file);

    /**
     * 知识库文档列表
     */
    List<KnowledgeDocVO> listDocs(Long knowledgeId);

    /**
     * 用户业务数据统计（工具 Agent 回调），跨用户时按 userId 精确过滤
     */
    UserStatsVO getUserStats(Long userId);
}
