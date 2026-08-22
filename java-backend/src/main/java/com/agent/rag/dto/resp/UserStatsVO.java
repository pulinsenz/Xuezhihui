package com.agent.rag.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户业务数据统计（供 Python Agent 工具回调，不暴露底层数据表结构）
 * <p>
 * 字段走 snake_case：与「Java ↔ Python Agent 契约」对齐。
 * Long 数字由全局 Jackson 配置序列化为字符串（雪花 ID 精度方案，统计值一并转字符串，前端/Python 仅转述）。
 *
 * @author pulinsenz
 */
@Data
public class UserStatsVO implements Serializable {

    /**
     * 用户 id（受信来源：Java 从 JWT 解析后透传，前端不可伪造）
     */
    private Long userId;

    /**
     * 知识库数量
     */
    @JsonProperty("knowledge_count")
    private Long knowledgeCount;

    /**
     * 文档总数
     */
    @JsonProperty("doc_count")
    private Long docCount;

    /**
     * 已向量化文档数
     */
    @JsonProperty("vector_success")
    private Long vectorSuccess;

    /**
     * 待向量化文档数
     */
    @JsonProperty("vector_pending")
    private Long vectorPending;

    /**
     * 向量化失败文档数
     */
    @JsonProperty("vector_failed")
    private Long vectorFailed;

    /**
     * 最近上传时间
     */
    @JsonProperty("last_upload_time")
    private LocalDateTime lastUploadTime;

    private static final long serialVersionUID = 1L;
}
