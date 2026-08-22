package com.agent.rag.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 删除向量请求（Java → Python Agent）
 *
 * @author pulinsenz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteVectorRequest implements Serializable {

    /**
     * 知识库 id
     */
    @JsonProperty("knowledge_id")
    private String knowledgeId;

    /**
     * 文档 id（可空，空则删整个知识库）
     */
    @JsonProperty("doc_id")
    private String docId;

    private static final long serialVersionUID = 1L;
}
