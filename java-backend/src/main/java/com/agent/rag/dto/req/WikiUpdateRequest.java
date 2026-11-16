package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 编辑 AI Wiki 页面请求（标题/正文）
 *
 * @author pulinsenz
 */
@Data
public class WikiUpdateRequest implements Serializable {

    /**
     * 页面标题（可空，空则不改）
     */
    private String title;

    /**
     * Markdown 正文（可空，空则不改）
     */
    private String content;

    private static final long serialVersionUID = 1L;
}
