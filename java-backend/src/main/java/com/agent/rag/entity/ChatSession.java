package com.agent.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话会话实体，对应数据库表 chat_session（历史持久化到 MySQL）
 *
 * @author pulinsenz
 */
@TableName(value = "chat_session")
@Data
public class ChatSession implements Serializable {

    /**
     * 会话 id（前端生成的 UUID 字符串）
     */
    @TableId(type = IdType.INPUT)
    private String sessionId;

    /**
     * 所属用户 id
     */
    private Long userId;

    /**
     * 会话标题（首条用户消息截断）
     */
    private String title;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
