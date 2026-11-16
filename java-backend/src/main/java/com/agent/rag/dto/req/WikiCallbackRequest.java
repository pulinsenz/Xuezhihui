package com.agent.rag.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Wiki 生成完成回调请求（Python worker → Java，X-Agent-Token 鉴权）。
 * <p>
 * 整批回调：任务结束时一次性带上全部页面（含失败页），Java 侧 SUCCESS 时
 * 逻辑删旧 Wiki + 插入新页；FAILED 时仅记录日志，旧 Wiki 保留可见。
 *
 * @author pulinsenz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WikiCallbackRequest implements Serializable {

    /**
     * 任务 id（与 Redis 任务 Hash 对应，仅日志/追踪用）
     */
    @JsonProperty("task_id")
    private String taskId;

    /**
     * 知识库 id
     */
    @JsonProperty("knowledge_id")
    private String knowledgeId;

    /**
     * 任务结果：SUCCESS / FAILED（≥1 页成功即 SUCCESS）
     */
    @JsonProperty("status")
    private String status;

    /**
     * 失败原因（FAILED 时）
     */
    @JsonProperty("message")
    private String message;

    /**
     * 生成的页面列表
     */
    @JsonProperty("pages")
    private List<WikiPageItem> pages;

    /**
     * 单个页面
     */
    @Data
    @NoArgsConstructor
    public static class WikiPageItem implements Serializable {

        @JsonProperty("title")
        private String title;

        @JsonProperty("summary")
        private String summary;

        /**
         * Markdown 正文（失败页为空）
         */
        @JsonProperty("content")
        private String content;

        /**
         * 来源文档 id JSON 数组字符串
         */
        @JsonProperty("source_doc_ids")
        private String sourceDocIds;

        /**
         * Milvus 中 doc_id（wiki-{uuid}）
         */
        @JsonProperty("vector_doc_id")
        private String vectorDocId;

        /**
         * 页面级状态：SUCCESS / FAILED
         */
        @JsonProperty("status")
        private String status;

        /**
         * 页面级失败原因
         */
        @JsonProperty("error_msg")
        private String errorMsg;

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
