package com.agent.rag.dto.req;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建知识库请求
 *
 * @author pulinsenz
 */
@Data
public class KnowledgeCreateRequest implements Serializable {

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

    private static final long serialVersionUID = 1L;
}
