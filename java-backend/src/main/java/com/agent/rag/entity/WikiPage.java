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
 * AI Wiki 页面实体，对应数据库表 wiki_page。
 * <p>
 * 由 Python Agent 依据知识库文档经 LLM 生成（大纲规划 → 逐页撰写），
 * 经 /internal/agent/wiki-callback 回写；每次生成成功整体替换旧页面。
 *
 * @author pulinsenz
 */
@TableName(value = "wiki_page")
@Data
public class WikiPage implements Serializable {

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
     * 页面标题
     */
    private String title;

    /**
     * 页面摘要
     */
    private String summary;

    /**
     * Markdown 正文
     */
    private String content;

    /**
     * 来源文档 id JSON 数组（如 ["1","3"]）
     */
    private String sourceDocIds;

    /**
     * Milvus 中 doc_id（wiki-{uuid}），删除 Wiki 时按它清向量
     */
    private String vectorDocId;

    /**
     * 页面顺序（从 1 开始）
     */
    private Integer sortOrder;

    /**
     * 单页生成失败原因（页面级容错：其余页照常入库）
     */
    private String errorMsg;

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
