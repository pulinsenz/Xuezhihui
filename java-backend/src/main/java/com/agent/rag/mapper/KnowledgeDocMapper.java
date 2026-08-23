package com.agent.rag.mapper;

import com.agent.rag.entity.KnowledgeDoc;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库文档 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface KnowledgeDocMapper extends BaseMapper<KnowledgeDoc> {

    /**
     * 分页查询知识库文档，手动控制逻辑删除条件（管理员查看含已删除文档）
     */
    @Select("""
            <script>
            SELECT id, knowledgeId, name, fileUrl, fileSize, fileType, vectorStatus, errorMsg, createTime, updateTime, isDelete
            FROM knowledge_doc
            WHERE knowledgeId = #{knowledgeId}
            AND (#{deleted} IS NULL OR isDelete = #{deleted})
            <if test="keyword != null and keyword != ''">
                AND name LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY createTime DESC
            </script>
            """)
    IPage<KnowledgeDoc> selectDocPage(Page<KnowledgeDoc> page,
                                      @Param("knowledgeId") Long knowledgeId,
                                      @Param("keyword") String keyword,
                                      @Param("deleted") Integer deleted);

    /**
     * 按 id 查询文档（不区分删除状态，管理员管理用）
     */
    @Select("SELECT id, knowledgeId, name, fileUrl, fileSize, fileType, vectorStatus, errorMsg, createTime, updateTime, isDelete FROM knowledge_doc WHERE id = #{id}")
    KnowledgeDoc selectAnyById(@Param("id") Long id);

    /**
     * 查询知识库下全部文档（不区分删除状态，恢复知识库时枚举重入库）
     */
    @Select("SELECT id, knowledgeId, name, fileUrl, fileSize, fileType, vectorStatus, errorMsg, createTime, updateTime, isDelete FROM knowledge_doc WHERE knowledgeId = #{knowledgeId}")
    List<KnowledgeDoc> selectAllByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 统计知识库下全部文档数（含已删除，管理员列表显示可恢复的文档数）
     */
    @Select("SELECT COUNT(*) FROM knowledge_doc WHERE knowledgeId = #{knowledgeId}")
    Long countAll(@Param("knowledgeId") Long knowledgeId);

    /**
     * 恢复已删除文档（绕过逻辑删除，直接置 isDelete=0）
     */
    @Update("UPDATE knowledge_doc SET isDelete = 0 WHERE id = #{id} AND isDelete = 1")
    int restoreDeleted(@Param("id") Long id);

    /**
     * 批量恢复知识库下全部已删除文档
     */
    @Update("UPDATE knowledge_doc SET isDelete = 0 WHERE knowledgeId = #{knowledgeId} AND isDelete = 1")
    int restoreDeletedByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
