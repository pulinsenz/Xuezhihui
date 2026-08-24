package com.agent.rag.dto.resp;

import com.agent.rag.entity.Knowledge;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库信息
 *
 * @author pulinsenz
 */
@Data
public class KnowledgeVO implements Serializable {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    private String cover;

    /**
     * 所属用户 id
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 是否公开：1=公开 0=私有
     */
    private Integer isPublic;

    /**
     * 浏览量
     */
    private Integer viewCount;

    /**
     * 收藏量
     */
    private Integer favoriteCount;

    /**
     * 文档数量
     */
    private Long docCount;

    /**
     * 逻辑删除状态：0=正常、1=已删除（管理员列表展示）
     */
    private Integer isDelete;

    /**
     * 作者昵称
     */
    private String authorName;

    /**
     * 作者头像
     */
    private String authorAvatar;

    /**
     * 当前用户是否为作者（拥有者）
     */
    private Boolean isOwner;

    /**
     * 当前用户是否为协作者
     */
    private Boolean isMember;

    /**
     * 当前用户是否已收藏
     */
    private Boolean isFavorite;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;

    public static KnowledgeVO from(Knowledge knowledge) {
        KnowledgeVO vo = new KnowledgeVO();
        vo.setId(knowledge.getId());
        vo.setName(knowledge.getName());
        vo.setDescription(knowledge.getDescription());
        vo.setCover(knowledge.getCover());
        vo.setUserId(knowledge.getUserId());
        vo.setIsPublic(knowledge.getIsPublic());
        vo.setViewCount(knowledge.getViewCount());
        vo.setFavoriteCount(knowledge.getFavoriteCount());
        vo.setCreateTime(knowledge.getCreateTime());
        vo.setIsDelete(knowledge.getIsDelete());
        return vo;
    }
}
