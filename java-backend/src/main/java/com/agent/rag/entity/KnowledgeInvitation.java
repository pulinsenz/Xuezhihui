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
 * 知识库邀请消息实体，对应 knowledge_invitation 表。
 */
@TableName(value = "knowledge_invitation")
@Data
public class KnowledgeInvitation implements Serializable {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private Long knowledgeId;

    private Long inviterId;

    private Long targetUserId;

    /**
     * PENDING / ACCEPTED / REJECTED
     */
    private String status;

    private String message;

    private LocalDateTime readTime;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
