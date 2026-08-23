package com.agent.rag.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 会话单条消息（对话历史）
 *
 * @author pulinsenz
 */
@Data
public class HistoryMessageVO implements Serializable {

    private String role;

    private String content;

    /**
     * 回答属性：kb/business/chitchat/other（assistant 消息）
     */
    private String route;

    /**
     * 使用的知识库 id（assistant 消息，kb 时；snake_case 与流式 done 事件对齐）
     */
    @JsonProperty("knowledge_id")
    private String knowledgeId;

    /**
     * 思考过程（assistant 消息）
     */
    private List<String> thinking;

    /**
     * 参考文献（assistant 消息）
     */
    private List<Map<String, Object>> sources;

    private static final long serialVersionUID = 1L;

    public static HistoryMessageVO of(String role, String content) {
        HistoryMessageVO vo = new HistoryMessageVO();
        vo.setRole(role);
        vo.setContent(content);
        return vo;
    }
}
