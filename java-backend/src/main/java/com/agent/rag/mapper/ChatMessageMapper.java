package com.agent.rag.mapper;

import com.agent.rag.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

}
