package com.agent.rag.dto.resp;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class RetrievalTestVO {
    @JsonAlias("elapsed_ms")
    private double elapsedMs;
    private List<Hit> hits;

    @Data
    public static class Hit {
        @JsonAlias("chunk_id")
        private String chunkId;
        @JsonAlias("doc_id")
        private String docId;
        private String docName;
        private String text;
        @JsonAlias("vector_rank")
        private Integer vectorRank;
        @JsonAlias("bm25_rank")
        private Integer bm25Rank;
        @JsonAlias("fusion_score")
        private double fusionScore;
        @JsonAlias("rerank_score")
        private Double rerankScore;
    }
}
