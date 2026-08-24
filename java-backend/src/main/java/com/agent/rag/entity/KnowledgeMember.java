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
 * 知识库协作者实体，对应数据库表 knowledge_member
 * <p>
 * 作者邀请他人共同管理知识库；作者权限最高，协作者可管理文档（上传/删除/入库），
 * 但不可删除知识库、修改知识库信息或管理成员。
 *
 * @author pulinsenz
 */
@TableName(value = "knowledge_member")
@Data
public class KnowledgeMember implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 所属知识库 id
     */
    private Long knowledgeId;

    /**
     * 协作者用户 id
     */
    private Long userId;

    /**
     * 加入时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
