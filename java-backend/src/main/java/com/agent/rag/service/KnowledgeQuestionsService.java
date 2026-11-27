package com.agent.rag.service;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeQuestionsMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeQuestionsService {
    @Resource
    private KnowledgeService knowledgeService;
    @Resource
    private KnowledgeQuestionsMapper mapper;
    @Resource
    private ObjectMapper objectMapper;

    public List<String> list(Long knowledgeId) {
        knowledgeService.getViewableKnowledge(knowledgeId);
        String json = mapper.findQuestions(knowledgeId);
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored knowledge questions", e);
        }
    }

    public void save(Long knowledgeId, List<String> questions) {
        knowledgeService.getOwnedKnowledge(knowledgeId);
        if (questions == null || questions.size() > 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "常用问题最多 8 个，传入空数组可清空");
        }
        List<String> normalized = new ArrayList<>();
        for (String question : questions) {
            if (question == null || question.isBlank() || question.trim().length() > 200) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "每个常用问题须为 1–200 个字符");
            }
            String text = question.trim();
            if (normalized.contains(text)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "常用问题不能重复");
            }
            normalized.add(text);
        }
        try {
            mapper.saveQuestions(knowledgeId, objectMapper.writeValueAsString(normalized));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize knowledge questions", e);
        }
    }
}
