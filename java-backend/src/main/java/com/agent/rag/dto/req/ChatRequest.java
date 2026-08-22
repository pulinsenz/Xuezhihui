package com.agent.rag.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 对话请求（字段名与 Python Agent 契约对齐：snake_case）
 *
 * @author pulinsenz
 */
@Data
public class ChatRequest implements Serializable {

    /**
     * 会话 id（前端可选传，不传则由 Java 生成）
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * 用户问题
     */
    private String query;

    /**
     * 知识库 id（可选）
     */
    @JsonProperty("knowledge_id")
    private String knowledgeId;

    /**
     * 用户 id（受信字段：仅 Java 从 JWT 解析后注入，前端传值会被覆盖，杜绝伪造身份）
     */
    @JsonProperty("user_id")
    private String userId;

    private static final long serialVersionUID = 1L;
}
