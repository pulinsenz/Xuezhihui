package com.agent.rag.dto.resp;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话单条消息（对话历史）
 *
 * @author pulinsenz
 */
@Data
public class HistoryMessageVO implements Serializable {

    private String role;

    private String content;

    private static final long serialVersionUID = 1L;

    public static HistoryMessageVO of(String role, String content) {
        HistoryMessageVO vo = new HistoryMessageVO();
        vo.setRole(role);
        vo.setContent(content);
        return vo;
    }
}
