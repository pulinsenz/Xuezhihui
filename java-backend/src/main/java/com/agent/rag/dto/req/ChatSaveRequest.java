package com.agent.rag.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 对话落库回调请求（Python → Java，字段名与 Python 契约对齐 snake_case）
 *
 * @author pulinsenz
 */
@Data
public class ChatSaveRequest implements Serializable {

    /**
     * 会话 id
     */
    @JsonProperty("session_id")
    private String sessionId;

    /**
     * 用户 id
     */
    @JsonProperty("user_id")
    private String userId;

    /**
     * 用户问题
     */
    private String query;

    /**
     * 助手完整回答
     */
    private String answer;

    private static final long serialVersionUID = 1L;
}
