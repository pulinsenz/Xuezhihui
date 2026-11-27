package com.agent.rag.dto.req;

import lombok.Data;
import java.util.List;

@Data
public class KnowledgeQuestionsRequest {
    private List<String> questions;
}
