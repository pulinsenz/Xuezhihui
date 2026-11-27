package com.agent.rag.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatConvenienceInitializerTest {
    @Mock private JdbcTemplate jdbc;
    private ChatConvenienceInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new ChatConvenienceInitializer();
        ReflectionTestUtils.setField(initializer, "jdbcTemplate", jdbc);
    }

    @Test
    void upgradesExistingDatabaseAndIsIdempotent() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString())).thenReturn(0, 0, 1, 1);
        initializer.run(null);
        initializer.run(null);
        verify(jdbc, times(1)).execute("ALTER TABLE `chat_session` ADD COLUMN `pinned` tinyint NOT NULL DEFAULT 0");
        verify(jdbc, times(1)).execute("ALTER TABLE `chat_message` ADD COLUMN `feedback` tinyint NOT NULL DEFAULT 0");
        verify(jdbc, times(2)).execute(contains("CREATE TABLE IF NOT EXISTS knowledge_questions"));
    }

    @Test
    void concurrentDuplicateColumnIsSafeButOtherErrorsFailStartup() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyString())).thenReturn(0);
        doThrow(new BadSqlGrammarException("alter", "", new SQLException("Duplicate column", "42S21", 1060)))
                .when(jdbc).execute(startsWith("ALTER TABLE"));
        assertDoesNotThrow(() -> initializer.run(null));
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(jdbc).execute(startsWith("ALTER TABLE"));
        assertThrows(DataAccessResourceFailureException.class, () -> initializer.run(null));
    }
}
