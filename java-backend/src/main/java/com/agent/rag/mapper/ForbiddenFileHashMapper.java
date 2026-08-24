package com.agent.rag.mapper;

import com.agent.rag.entity.ForbiddenFileHash;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 封禁文件哈希黑名单 Mapper
 *
 * @author pulinsenz
 */
@Mapper
public interface ForbiddenFileHashMapper extends BaseMapper<ForbiddenFileHash> {

    /**
     * 该文件哈希是否被管理员封禁（全局禁止上传/恢复）
     */
    @Select("SELECT COUNT(*) > 0 FROM forbidden_file_hash WHERE fileHash = #{fileHash}")
    boolean existsByHash(@Param("fileHash") String fileHash);

    /**
     * 记录封禁哈希（INSERT IGNORE 幂等，唯一键去重，重复封禁不报错）
     */
    @Insert("INSERT IGNORE INTO forbidden_file_hash (fileHash) VALUES (#{fileHash})")
    int insertIgnore(@Param("fileHash") String fileHash);
}
