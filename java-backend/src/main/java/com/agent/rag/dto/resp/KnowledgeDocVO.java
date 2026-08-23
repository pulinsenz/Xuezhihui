package com.agent.rag.dto.resp;

import com.agent.rag.entity.KnowledgeDoc;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档信息
 *
 * @author pulinsenz
 */
@Data
public class KnowledgeDocVO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    private String name;

    private String fileUrl;

    private Long fileSize;

    private String fileType;

    private String vectorStatus;

    private String errorMsg;

    /**
     * 逻辑删除状态：0=正常、1=已删除（管理员列表展示）
     */
    private Integer isDelete;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;

    public static KnowledgeDocVO from(KnowledgeDoc doc) {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        vo.setId(doc.getId());
        vo.setKnowledgeId(doc.getKnowledgeId());
        vo.setName(doc.getName());
        vo.setFileUrl(doc.getFileUrl());
        vo.setFileSize(doc.getFileSize());
        vo.setFileType(doc.getFileType());
        vo.setVectorStatus(doc.getVectorStatus());
        vo.setErrorMsg(doc.getErrorMsg());
        vo.setIsDelete(doc.getIsDelete());
        vo.setCreateTime(doc.getCreateTime());
        return vo;
    }
}
