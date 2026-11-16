package com.agent.rag.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时确保 AI Wiki 页面表存在（已部署库免手动迁移）。
 */
@Slf4j
@Component
public class WikiPageInitializer implements ApplicationRunner {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `wiki_page`
                (
                    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
                    `knowledgeId`  bigint       NOT NULL COMMENT '所属知识库id',
                    `title`        varchar(256) NOT NULL COMMENT '页面标题',
                    `summary`      varchar(1024) DEFAULT NULL COMMENT '页面摘要',
                    `content`      mediumtext   COMMENT 'Markdown 正文',
                    `sourceDocIds` varchar(1024) DEFAULT NULL COMMENT '来源文档id JSON数组',
                    `vectorDocId`  varchar(64)  DEFAULT NULL COMMENT 'Milvus 中 doc_id(wiki-{uuid})，删 Wiki 时清向量用',
                    `sortOrder`    int          NOT NULL DEFAULT 0 COMMENT '页面顺序',
                    `errorMsg`     varchar(512) DEFAULT NULL COMMENT '单页生成失败原因',
                    `createTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    `updateTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    `isDelete`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除',
                    PRIMARY KEY (`id`),
                    KEY `idx_knowledgeId` (`knowledgeId`)
                ) ENGINE = InnoDB
                  DEFAULT CHARSET = utf8mb4
                  COLLATE = utf8mb4_unicode_ci COMMENT ='AI Wiki 页面'
                """);
        log.debug("wiki_page 表已检查完毕");
    }
}
