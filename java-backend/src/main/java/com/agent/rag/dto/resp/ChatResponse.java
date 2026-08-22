package com.agent.rag.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 对话响应（对齐 Python Agent 返回结构）
 *
 * @author pulinsenz
 */
@Data
public class ChatResponse implements Serializable {

    /**
     * 回答内容
     */
    private String answer;

    /**
     * 引用来源
     */
    private List<SourceVO> sources;

    /**
     * 路由结果：kb / chitchat / other
     */
    private String route;

    /**
     * 会话 id
     */
    @JsonProperty("session_id")
    private String sessionId;

    private static final long serialVersionUID = 1L;

    @Data
    public static class SourceVO implements Serializable {
        private String text;
        private Double score;
    }
}
