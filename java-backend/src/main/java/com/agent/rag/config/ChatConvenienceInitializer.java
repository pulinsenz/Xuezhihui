package com.agent.rag.config;

import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;

/** 与现有初始化器一致，兼容已经部署的数据库。 */
@Component
public class ChatConvenienceInitializer implements ApplicationRunner {
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        addColumn("chat_session", "pinned", "tinyint NOT NULL DEFAULT 0");
        addColumn("chat_message", "feedback", "tinyint NOT NULL DEFAULT 0");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_questions (
                    knowledgeId bigint NOT NULL PRIMARY KEY,
                    questions text NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void addColumn(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, table, column);
        if (count != null && count > 0) return;
        try {
            // 参数仅来自本类固定常量，绝不接收用户输入。
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        } catch (DataAccessException e) {
            // 多实例同时启动只容忍 MySQL 的重复列错误，其余迁移错误应阻止启动。
            if (!(e.getMostSpecificCause() instanceof java.sql.SQLException sql) || sql.getErrorCode() != 1060) throw e;
        }
    }
}
