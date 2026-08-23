package com.agent.rag.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 会话列表项（对话历史侧栏）
 *
 * @author pulinsenz
 */
@Data
public class SessionVO implements Serializable {

    @JsonProperty("session_id")
    private String sessionId;

    private String title;

    @JsonProperty("update_time")
    private Long updateTime;

    @JsonProperty("message_count")
    private Long messageCount;

    private static final long serialVersionUID = 1L;
}
