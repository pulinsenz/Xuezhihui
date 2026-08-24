package com.agent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 封禁文件哈希黑名单实体，对应数据库表 forbidden_file_hash
 * <p>
 * 管理员删除成员文档时记录其内容 SHA-256；命中黑名单的文件全局禁止上传与恢复。
 * 表无 isDelete 列，不受 MyBatis-Plus 逻辑删除影响。
 *
 * @author pulinsenz
 */
@TableName(value = "forbidden_file_hash")
@Data
public class ForbiddenFileHash implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 被管理员删除的文件内容 SHA-256（全局唯一封禁）
     */
    private String fileHash;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
