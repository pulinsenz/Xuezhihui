package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新知识库请求（仅作者可操作）
 *
 * @author pulinsenz
 */
@Data
public class KnowledgeUpdateRequest implements Serializable {

    /**
     * 知识库 id
     */
    private Long id;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库简介
     */
    private String description;

    /**
     * 知识库封面
     */
    private String cover;

    /**
     * 是否公开：1=公开 0=私有
     */
    private Integer isPublic;

    private static final long serialVersionUID = 1L;
}
