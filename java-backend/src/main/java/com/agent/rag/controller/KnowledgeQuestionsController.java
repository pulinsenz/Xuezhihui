package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.KnowledgeQuestionsRequest;
import com.agent.rag.service.KnowledgeQuestionsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/knowledge/{id}/questions")
public class KnowledgeQuestionsController {
    @Resource
    private KnowledgeQuestionsService service;

    @GetMapping
    public Result<List<String>> list(@PathVariable Long id) {
        return Result.success(service.list(id));
    }

    @PutMapping
    public Result<Boolean> save(@PathVariable Long id, @RequestBody KnowledgeQuestionsRequest request) {
        service.save(id, request.getQuestions());
        return Result.success(true);
    }
}
