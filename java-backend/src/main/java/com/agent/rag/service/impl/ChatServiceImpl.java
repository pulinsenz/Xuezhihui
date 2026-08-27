package com.agent.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.dto.req.ChatRequest;
import com.agent.rag.dto.resp.ChatResponse;
import com.agent.rag.dto.resp.HistoryMessageVO;
import com.agent.rag.dto.resp.SessionVO;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.service.ChatHistoryService;
import com.agent.rag.service.ChatService;
import com.agent.rag.util.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 对话服务实现
 * <p>
 * 普通对话走 Feign；SSE 流式用 JDK HttpClient 流式读取 Python SSE 并逐行转发，
 * 避免 Feign 对流式响应的限制。
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Resource
    private PythonAgentClient pythonAgentClient;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Value("${app.agent.base-url}")
    private String agentBaseUrl;

    @Value("${app.agent.token:}")
    private String agentToken;

    @Override
    public ChatResponse chat(ChatRequest request) {
        validate(request);
        if (StrUtil.isBlank(request.getSessionId())) {
            request.setSessionId(IdUtil.fastSimpleUUID());
        }
        // 写侧越权防护：会话已存在则必须属于当前用户
        chatHistoryService.checkAccess(request.getSessionId(), requireUserId());
        fillUserId(request);
        com.agent.rag.common.Result<ChatResponse> resp = pythonAgentClient.chat(request);
        if (resp == null || resp.getCode() != 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "对话服务异常: " + (resp == null ? "无响应" : resp.getMessage()));
        }
        return resp.getData();
    }

    @Override
    public SseEmitter stream(ChatRequest request) {
        validate(request);
        if (StrUtil.isBlank(request.getSessionId())) {
            request.setSessionId(IdUtil.fastSimpleUUID());
        }
        // 写侧越权防护：会话已存在则必须属于当前用户
        chatHistoryService.checkAccess(request.getSessionId(), requireUserId());
        SseEmitter emitter = new SseEmitter(300_000L);
        String url = buildStreamUrl(request);
        log.info("SSE 透传开始: sessionId={}, pythonUrl={}", request.getSessionId(), url);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .GET();
        // 内部鉴权：Python Agent 侧校验
        if (StrUtil.isNotBlank(agentToken)) {
            requestBuilder.header("X-Agent-Token", agentToken);
        }
        HttpRequest httpRequest = requestBuilder.build();

        client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    try {
                        // forEach 惰性逐行消费 → 实时转发 Python 的 SSE 事件（剥掉 "data: " 前缀）
                        response.body().forEach(line -> {
                            if (line.startsWith("data: ")) {
                                try {
                                    emitter.send(line.substring(6));
                                } catch (Exception sendEx) {
                                    throw new RuntimeException(sendEx);
                                }
                            }
                        });
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("SSE 转发异常: sessionId={}", request.getSessionId(), e);
                        emitter.completeWithError(e);
                    }
                })
                .exceptionally(e -> {
                    log.error("调用 Python Agent 流式接口失败: {}", url, e);
                    try {
                        emitter.send("{\"type\":\"error\",\"message\":\"对话服务暂不可用，请稍后再试\"}");
                    } catch (Exception ignored) {
                    }
                    emitter.complete();
                    return null;
                });
        return emitter;
    }

    @Override
    public List<SessionVO> listSessions() {
        return chatHistoryService.listSessions(requireUserId());
    }

    @Override
    public List<HistoryMessageVO> getSessionHistory(String sessionId) {
        return chatHistoryService.getHistory(sessionId, requireUserId());
    }

    @Override
    public void deleteSession(String sessionId) {
        chatHistoryService.deleteSession(sessionId, requireUserId());
    }

    private void validate(ChatRequest request) {
        if (request == null || StrUtil.isBlank(request.getQuery())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题内容不能为空");
        }
    }

    /**
     * 注入受信用户身份与角色：userId / userRole 只能来自登录态（JWT → ThreadLocal），
     * 前端传的 user_id / user_role 一律以当前登录用户为准，杜绝身份伪造（Python 侧据此选 LLM）
     */
    private void fillUserId(ChatRequest request) {
        User user = UserContext.getUser();
        if (user != null) {
            request.setUserId(String.valueOf(user.getId()));
            request.setUserRole(user.getUserRole());
        }
    }

    private String currentUserId() {
        User user = UserContext.getUser();
        return user == null ? null : String.valueOf(user.getId());
    }

    private String currentUserRole() {
        User user = UserContext.getUser();
        return user == null ? null : user.getUserRole();
    }

    /**
     * 当前登录用户 id（/chat/** 已被拦截器保护，正常必有登录态）
     */
    private Long requireUserId() {
        User user = UserContext.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "未登录");
        }
        return user.getId();
    }

    private String buildStreamUrl(ChatRequest request) {
        StringBuilder url = new StringBuilder(agentBaseUrl)
                .append("/api/agent/stream?")
                .append("session_id=").append(urlEncode(request.getSessionId()))
                .append("&query=").append(urlEncode(request.getQuery()));
        if (StrUtil.isNotBlank(request.getKnowledgeId())) {
            url.append("&knowledge_id=").append(urlEncode(request.getKnowledgeId()));
        }
        // 受信身份随 SSE 透传：工具 Agent 回调业务数据需要
        String userId = currentUserId();
        if (userId != null) {
            url.append("&user_id=").append(urlEncode(userId));
        }
        // 受信角色随 SSE 透传：Python 据此选普通用户/管理员 LLM
        String userRole = currentUserRole();
        if (StrUtil.isNotBlank(userRole)) {
            url.append("&user_role=").append(urlEncode(userRole));
        }
        return url.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
