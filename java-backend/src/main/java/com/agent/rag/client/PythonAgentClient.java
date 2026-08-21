package com.agent.rag.client;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.VectorizeRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Python Agent 服务客户端（Feign）
 * <p>
 * 调用 Python 侧接口完成文档向量化入库等 AI 逻辑
 *
 * @author pulinsenz
 */
@FeignClient(name = "python-agent", url = "${app.agent.base-url}")
public interface PythonAgentClient {

    /**
     * 文档向量化入库
     */
    @PostMapping("/api/knowledge/vectorize")
    Result<Void> vectorize(@RequestBody VectorizeRequest request);
}
