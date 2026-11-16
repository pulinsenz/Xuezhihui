package com.agent.rag.mapper;

import com.agent.rag.entity.WikiPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI Wiki 页面 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface WikiPageMapper extends BaseMapper<WikiPage> {
}
