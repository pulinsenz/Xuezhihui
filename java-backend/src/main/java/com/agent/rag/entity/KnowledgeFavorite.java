package com.agent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库收藏实体，对应数据库表 knowledge_favorite
 * <p>
 * 用户收藏他人公开知识库后，该知识库出现在自己的知识库列表里。
 *
 * @author pulinsenz
 */
@TableName(value = "knowledge_favorite")
@Data
public class KnowledgeFavorite implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 被收藏的知识库 id
     */
    private Long knowledgeId;

    /**
     * 收藏用户 id
     */
    private Long userId;

    /**
     * 收藏时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
