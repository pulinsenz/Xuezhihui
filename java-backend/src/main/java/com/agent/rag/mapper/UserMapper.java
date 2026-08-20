package com.agent.rag.mapper;

import com.agent.rag.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
