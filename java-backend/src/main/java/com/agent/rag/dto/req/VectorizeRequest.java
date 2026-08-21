package com.agent.rag.dto.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文档向量化请求（Java → Python Agent）
 *
 * @author pulinsenz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorizeRequest implements Serializable {

    /**
     * 知识库 id
     */
    private Long knowledgeId;

    /**
     * 文档 id
     */
    private Long docId;

    /**
     * 文件存储地址
     */
    private String fileUrl;

    /**
     * 文件名
     */
    private String name;

    private static final long serialVersionUID = 1L;
}
