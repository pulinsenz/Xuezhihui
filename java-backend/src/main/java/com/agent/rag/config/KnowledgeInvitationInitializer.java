package com.agent.rag.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时确保知识库邀请消息表存在。
 */
@Slf4j
@Component
public class KnowledgeInvitationInitializer implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `knowledge_invitation`
                (
                    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
                    `knowledgeId`  bigint       NOT NULL COMMENT '知识库id',
                    `inviterId`    bigint       NOT NULL COMMENT '邀请人id',
                    `targetUserId` bigint       NOT NULL COMMENT '被邀请人id',
                    `status`       varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/ACCEPTED/REJECTED',
                    `message`      varchar(1000) DEFAULT NULL COMMENT '邀请文案',
                    `readTime`     datetime DEFAULT NULL COMMENT '阅读时间',
                    `handleTime`   datetime DEFAULT NULL COMMENT '处理时间',
                    `createTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    `updateTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    PRIMARY KEY (`id`),
                    KEY `idx_targetUserId` (`targetUserId`),
                    KEY `idx_inviterId` (`inviterId`),
                    KEY `idx_knowledgeId` (`knowledgeId`),
                    KEY `idx_status` (`status`)
                ) ENGINE = InnoDB
                  DEFAULT CHARSET = utf8mb4
                  COLLATE = utf8mb4_unicode_ci COMMENT ='知识库邀请消息'
                """);
        log.debug("knowledge_invitation 表已检查完毕");
    }
}
