package com.agent.rag.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 长任务状态（供前端轮询）
 * <p>
 * 状态值：PENDING（已提交）/ PROCESSING（Python worker 执行中）/ SUCCESS / FAILED
 *
 * @author pulinsenz
 */
@Data
public class TaskVO implements Serializable {

    /**
     * 任务 id（雪花 id 字符串）
     */
    private String taskId;

    /**
     * 任务类型：vectorize 等
     */
    private String type;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 结果/失败信息
     */
    private String message;

    /**
     * 创建时间
     */
    private String createTime;

    /**
     * 完成时间
     */
    private String finishTime;

    private static final long serialVersionUID = 1L;
}
