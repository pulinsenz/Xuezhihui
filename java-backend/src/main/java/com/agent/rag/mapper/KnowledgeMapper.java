package com.agent.rag.mapper;

import com.agent.rag.entity.Knowledge;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<Knowledge> {

}
