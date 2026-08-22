package com.agent.rag.mapper;

import com.agent.rag.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 分页查询用户，手动控制逻辑删除条件
     * <p>
     * BaseMapper.selectPage 会自动追加 isDelete=0（逻辑删除过滤），无法查已删除用户，
     * 因此手写 SQL 用 deleted 参数控制：null=全部、0=正常、1=已删除
     */
    @Select("""
            <script>
            SELECT id, userAccount, userName, userAvatar, userProfile, userRole, createTime, isDelete
            FROM `user`
            WHERE (#{deleted} IS NULL OR isDelete = #{deleted})
            <if test="keyword != null and keyword != ''">
                AND (userAccount LIKE CONCAT('%', #{keyword}, '%') OR userName LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY createTime DESC
            </script>
            """)
    IPage<User> selectUserPage(Page<User> page,
                               @Param("keyword") String keyword,
                               @Param("deleted") Integer deleted);

    /**
     * 恢复已删除用户（绕过逻辑删除，直接置 isDelete=0）
     */
    @Update("UPDATE `user` SET isDelete = 0 WHERE id = #{id} AND isDelete = 1")
    int restoreDeleted(@Param("id") Long id);
}
