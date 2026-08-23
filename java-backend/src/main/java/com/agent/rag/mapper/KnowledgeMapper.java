package com.agent.rag.mapper;

import com.agent.rag.entity.Knowledge;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<Knowledge> {

    /**
     * 分页查询知识库，手动控制逻辑删除条件
     * <p>
     * BaseMapper.selectPage 会自动追加 isDelete=0（逻辑删除过滤），无法查已删除知识库，
     * 因此手写 SQL 用 deleted 参数控制：null=全部、0=正常、1=已删除
     */
    @Select("""
            <script>
            SELECT id, name, description, cover, userId, createTime, updateTime, isDelete
            FROM knowledge
            WHERE (#{deleted} IS NULL OR isDelete = #{deleted})
            <if test="keyword != null and keyword != ''">
                AND name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY createTime DESC
            </script>
            """)
    IPage<Knowledge> selectKnowledgePage(Page<Knowledge> page,
                                         @Param("keyword") String keyword,
                                         @Param("deleted") Integer deleted);

    /**
     * 按 id 查询知识库（不区分删除状态，管理员管理用）
     */
    @Select("SELECT id, name, description, cover, userId, createTime, updateTime, isDelete FROM knowledge WHERE id = #{id}")
    Knowledge selectAnyById(@Param("id") Long id);

    /**
     * 恢复已删除知识库（绕过逻辑删除，直接置 isDelete=0）
     */
    @Update("UPDATE knowledge SET isDelete = 0 WHERE id = #{id} AND isDelete = 1")
    int restoreDeleted(@Param("id") Long id);
}
