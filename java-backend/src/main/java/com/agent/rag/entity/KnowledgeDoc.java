package com.agent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档实体，对应数据库表 knowledge_doc
 *
 * @author pulinsenz
 */
@TableName(value = "knowledge_doc")
@Data
public class KnowledgeDoc implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 所属知识库 id
     */
    private Long knowledgeId;

    /**
     * 原始文件名
     */
    private String name;

    /**
     * 文件存储地址
     */
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 向量化状态：PENDING/SUCCESS/FAILED
     */
    private String vectorStatus;

    /**
     * 向量化失败原因（或 SKIPPED 时的提示）
     */
    private String errorMsg;

    /**
     * 文件内容 SHA-256（上传去重用）
     */
    private String fileHash;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
