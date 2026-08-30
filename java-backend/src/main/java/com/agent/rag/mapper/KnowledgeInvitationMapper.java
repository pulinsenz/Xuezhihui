package com.agent.rag.mapper;

import com.agent.rag.entity.KnowledgeInvitation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库邀请消息 Mapper。
 */
@Mapper
public interface KnowledgeInvitationMapper extends BaseMapper<KnowledgeInvitation> {
}
