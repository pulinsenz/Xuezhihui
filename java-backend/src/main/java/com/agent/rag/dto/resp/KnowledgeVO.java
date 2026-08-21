package com.agent.rag.dto.resp;

import com.agent.rag.entity.Knowledge;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库信息
 *
 * @author pulinsenz
 */
@Data
public class KnowledgeVO implements Serializable {

    private Long id;

    private String name;

    private String description;

    private String cover;

    /**
     * 文档数量
     */
    private Long docCount;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;

    public static KnowledgeVO from(Knowledge knowledge) {
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(knowledge.getId());
        vo.setName(knowledge.getName());
        vo.setDescription(knowledge.getDescription());
        vo.setCover(knowledge.getCover());
        vo.setCreateTime(knowledge.getCreateTime());
        return vo;
    }
}
