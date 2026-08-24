package com.agent.rag.mapper;

import com.agent.rag.entity.KnowledgeFavorite;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 知识库收藏 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface KnowledgeFavoriteMapper extends BaseMapper<KnowledgeFavorite> {

    /**
     * INSERT IGNORE：唯一键 (knowledgeId,userId) 冲突时忽略，返回受影响行数（1=新增 0=已存在）
     */
    @Insert("INSERT IGNORE INTO knowledge_favorite (knowledgeId, userId, createTime) VALUES (#{knowledgeId}, #{userId}, NOW())")
    int insertIgnore(@Param("knowledgeId") Long knowledgeId, @Param("userId") Long userId);

    /**
     * 删除收藏关系，返回受影响行数（1=删除 0=不存在）
     */
    @Delete("DELETE FROM knowledge_favorite WHERE knowledgeId = #{knowledgeId} AND userId = #{userId}")
    int deleteByKnowledgeAndUser(@Param("knowledgeId") Long knowledgeId, @Param("userId") Long userId);
}
