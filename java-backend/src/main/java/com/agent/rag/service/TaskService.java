package com.agent.rag.service;

import com.agent.rag.dto.req.VectorizeCallbackRequest;
import com.agent.rag.dto.resp.TaskVO;
import com.agent.rag.entity.KnowledgeDoc;

import java.util.List;

/**
 * 长任务服务：Redis List 消息队列（Java 提交 / Python worker 消费）+ 任务状态查询
 *
 * @author pulinsenz
 */
public interface TaskService {

    /**
     * 提交文档向量化任务：写任务状态（Redis Hash）+ 入队（Redis List）
     *
     * @return 任务 id（供前端轮询）
     */
    String publishVectorize(Long knowledgeId, Long docId, String fileUrl, String name);

    /**
     * 提交 AI Wiki 生成任务：携带知识库全部待整理文档（名称 + 预签名地址），
     * Python worker 解析 → LLM 大纲规划 → 逐页撰写 → 回调 Java 回写 wiki_page
     *
     * @return 任务 id（供前端轮询）
     */
    String publishWiki(Long knowledgeId, List<KnowledgeDoc> docs);

    /**
     * 查询任务状态（校验归属：只能查当前登录用户的任务）
     */
    TaskVO getTask(String taskId);

    /**
     * Python worker 完成回调：回写文档向量化状态（Python 不直连 MySQL）
     */
    void completeVectorize(VectorizeCallbackRequest request);
}
