package com.agent.rag.client;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.ChatRequest;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.resp.ChatResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Python Agent 服务客户端（Feign）
 * <p>
 * 调用 Python 侧接口完成向量删除、对话等 AI 逻辑。
 * 文档向量化已迁移到 Redis 消息队列（TaskService），不再走同步 HTTP。
 *
 * @author pulinsenz
 */
@FeignClient(name = "python-agent", url = "${app.agent.base-url}")
public interface PythonAgentClient {

    /**
     * 删除向量（文档或整个知识库）
     */
    @PostMapping("/api/knowledge/delete")
    Result<Void> deleteKnowledge(@RequestBody DeleteVectorRequest request);

    /**
     * 普通对话（非流式）
     */
    @PostMapping("/api/agent/chat")
    Result<ChatResponse> chat(@RequestBody ChatRequest request);
}
