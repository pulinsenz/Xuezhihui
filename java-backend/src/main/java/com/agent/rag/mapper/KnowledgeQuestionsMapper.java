package com.agent.rag.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeQuestionsMapper {
    @Select("SELECT questions FROM knowledge_questions WHERE knowledgeId = #{knowledgeId}")
    String findQuestions(Long knowledgeId);

    @Insert("""
            INSERT INTO knowledge_questions (knowledgeId, questions) VALUES (#{knowledgeId}, #{questions})
            ON DUPLICATE KEY UPDATE questions = VALUES(questions)
            """)
    void saveQuestions(@Param("knowledgeId") Long knowledgeId, @Param("questions") String questions);
}
