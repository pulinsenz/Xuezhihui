package com.agent.rag.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库邀请消息展示对象。
 */
@Data
public class KnowledgeInvitationVO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeId;

    private String knowledgeName;

    private String knowledgeCover;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long inviterId;

    private String inviterName;

    private String inviterAvatar;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetUserId;

    private String targetUserName;

    private String targetUserAvatar;

    /**
     * incoming / outgoing
     */
    private String direction;

    private String status;

    private String message;

    private LocalDateTime readTime;

    private LocalDateTime handleTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
