package com.agent.rag.service;

import com.agent.rag.dto.req.VectorizeCallbackRequest;
import com.agent.rag.dto.resp.TaskVO;

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
     * 查询任务状态（校验归属：只能查当前登录用户的任务）
     */
    TaskVO getTask(String taskId);

    /**
     * Python worker 完成回调：回写文档向量化状态（Python 不直连 MySQL）
     */
    void completeVectorize(VectorizeCallbackRequest request);
}
