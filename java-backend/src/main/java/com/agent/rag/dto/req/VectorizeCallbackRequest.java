package com.agent.rag.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Python Agent 向量化任务完成回调（internal，Java 校验 X-Agent-Token）
 * <p>
 * 信任边界：Python worker 不直连 MySQL，任务结果经此回调回写文档向量化状态。
 *
 * @author pulinsenz
 */
@Data
public class VectorizeCallbackRequest implements Serializable {

    /**
     * 文档 id
     */
    @JsonProperty("doc_id")
    private String docId;

    /**
     * 状态：SUCCESS / FAILED
     */
    private String status;

    /**
     * 失败原因
     */
    @JsonProperty("error_msg")
    private String errorMsg;

    private static final long serialVersionUID = 1L;
}
