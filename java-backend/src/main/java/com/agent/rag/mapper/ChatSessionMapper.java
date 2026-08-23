package com.agent.rag.mapper;

import com.agent.rag.entity.ChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

}
