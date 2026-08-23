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
            SELECT id, knowledgeId, name, fileUrl, fileSize, fileType, vectorStatus, errorMsg, fileHash, deleteSource, createTime, updateTime, isDelete
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
    @Select("SELECT id, knowledgeId, name, fileUrl, fileSize, fileType, vectorStatus, errorMsg, fileHash, deleteSource, createTime, updateTime, isDelete FROM knowledge_doc WHERE id = #{id}")
    KnowledgeDoc selectAnyById(@Param("id") Long id);

    /**
     * 查询知识库下全部文档（不区分删除状态，恢复知识库时枚举重入库）
     */
    @Select("SELECT id, knowledgeId, name, fileUrl, fileSize, fileType, vectorStatus, errorMsg, fileHash, deleteSource, createTime, updateTime, isDelete FROM knowledge_doc WHERE knowledgeId = #{knowledgeId}")
    List<KnowledgeDoc> selectAllByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 统计知识库下全部文档数（含已删除，管理员列表显示可恢复的文档数）
     */
    @Select("SELECT COUNT(*) FROM knowledge_doc WHERE knowledgeId = #{knowledgeId}")
    Long countAll(@Param("knowledgeId") Long knowledgeId);

    /**
     * 统计同内容且处于"非删除或管理员删除"状态的文档数（用户上传去重/禁止上传用）
     */
    @Select("SELECT COUNT(*) FROM knowledge_doc WHERE knowledgeId = #{knowledgeId} AND fileHash = #{fileHash} "
            + "AND (isDelete = 0 OR (isDelete = 1 AND deleteSource = 'admin'))")
    Long countBlockingByHash(@Param("knowledgeId") Long knowledgeId, @Param("fileHash") String fileHash);

    /**
     * 统计同内容且被管理员删除的文档数（禁止用户再上传）
     */
    @Select("SELECT COUNT(*) FROM knowledge_doc WHERE knowledgeId = #{knowledgeId} AND fileHash = #{fileHash} "
            + "AND isDelete = 1 AND deleteSource = 'admin'")
    Long countAdminDeletedByHash(@Param("knowledgeId") Long knowledgeId, @Param("fileHash") String fileHash);

    /**
     * 恢复已删除文档（绕过逻辑删除，直接置 isDelete=0 并清空删除来源）
     */
    @Update("UPDATE knowledge_doc SET isDelete = 0, deleteSource = NULL WHERE id = #{id} AND isDelete = 1")
    int restoreDeleted(@Param("id") Long id);

    /**
     * 批量恢复知识库下全部已删除文档
     */
    @Update("UPDATE knowledge_doc SET isDelete = 0, deleteSource = NULL WHERE knowledgeId = #{knowledgeId} AND isDelete = 1")
    int restoreDeletedByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /**
     * 逻辑删除单个文档并记录删除来源（绕过 @TableLogic 直接置 isDelete=1）
     */
    @Update("UPDATE knowledge_doc SET isDelete = 1, deleteSource = #{deleteSource} WHERE id = #{id} AND isDelete = 0")
    int markDeleted(@Param("id") Long id, @Param("deleteSource") String deleteSource);

    /**
     * 逻辑删除知识库下全部正常文档并记录删除来源
     */
    @Update("UPDATE knowledge_doc SET isDelete = 1, deleteSource = #{deleteSource} WHERE knowledgeId = #{knowledgeId} AND isDelete = 0")
    int markDeletedByKnowledgeId(@Param("knowledgeId") Long knowledgeId, @Param("deleteSource") String deleteSource);

    /**
     * 用户文档列表（deleted 过滤：null=全部/0=正常/1=已删除；用户可看到自己删除的文档并恢复）
     */
    @Select("""
            <script>
            SELECT id, knowledgeId, name, fileUrl, fileSize, fileType, vectorStatus, errorMsg, fileHash, deleteSource, createTime, updateTime, isDelete
            FROM knowledge_doc
            WHERE knowledgeId = #{knowledgeId}
            AND (#{deleted} IS NULL OR isDelete = #{deleted})
            ORDER BY createTime DESC
            </script>
            """)
    List<KnowledgeDoc> selectDocsByFilter(@Param("knowledgeId") Long knowledgeId, @Param("deleted") Integer deleted);
}
