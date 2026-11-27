package com.agent.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.rag.dto.req.ChatRequest;
import com.agent.rag.dto.resp.ChatResponse;
import com.agent.rag.dto.resp.HistoryMessageVO;
import com.agent.rag.dto.resp.SessionVO;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.service.ChatHistoryService;
import com.agent.rag.service.ChatRateLimitService;
import com.agent.rag.service.ChatService;
import com.agent.rag.service.KnowledgeService;
import com.agent.rag.util.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
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

    /** 全进程共享的 HTTP 客户端（线程安全，连接池复用） */
    private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private PythonAgentClient pythonAgentClient;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Value("${app.agent.base-url}")
    private String agentBaseUrl;

    @Value("${app.agent.token:}")
    private String agentToken;

    @Resource
    private ChatRateLimitService chatRateLimitService;

    @Resource
    private KnowledgeService knowledgeService;

    @Override
    public ChatResponse chat(ChatRequest request) {
        validate(request);
        // 越权防护：knowledge_id 会透传给 Python Agent 检索，转发前必须校验当前用户可查看该知识库，
        // 否则任意登录用户可引用他人私有知识库的 id 读取其文档内容（跨租户数据泄露）
        checkKnowledgeAccess(request.getKnowledgeId());
        if (StrUtil.isBlank(request.getSessionId())) {
            request.setSessionId(IdUtil.fastSimpleUUID());
        }
        // 用户级对话限流：防脚本刷 LLM token（普通用户配额，admin 不限）
        enforceRateLimit();
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
        // 越权防护：knowledge_id 透传 Python 检索前校验查看权限（与 chat 同规）
        checkKnowledgeAccess(request.getKnowledgeId());
        if (StrUtil.isBlank(request.getSessionId())) {
            request.setSessionId(IdUtil.fastSimpleUUID());
        }
        // 用户级对话限流：防脚本刷 LLM token（普通用户配额，admin 不限）
        enforceRateLimit();
        // 写侧越权防护：会话已存在则必须属于当前用户
        chatHistoryService.checkAccess(request.getSessionId(), requireUserId());
        SseEmitter emitter = new SseEmitter(300_000L);
        // 受信身份随 body 透传（替代原 GET query 传参）：工具 Agent 回调业务数据 + Python 选 LLM 需要
        fillUserId(request);
        log.info("SSE 透传开始: sessionId={}", request.getSessionId());

        // HttpClient 线程安全，全进程共享（连接池复用；旧实现每次请求 new 一个）
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(agentBaseUrl + "/api/agent/stream"))
                .timeout(Duration.ofSeconds(120))
                // POST + JSON body：用户提问不再以 query 形式全文落 Java/Python 两级访问日志
                // ChatRequest 序列化即 snake_case（@JsonProperty），与 Python 模型契约一致
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toAgentJson(request), StandardCharsets.UTF_8));
        // 内部鉴权：Python Agent 侧校验
        if (StrUtil.isNotBlank(agentToken)) {
            requestBuilder.header("X-Agent-Token", agentToken);
        }
        HttpRequest httpRequest = requestBuilder.build();

        SHARED_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofLines())
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
                    log.error("调用 Python Agent 流式接口失败: sessionId={}", request.getSessionId(), e);
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

    @Override
    public void renameSession(String sessionId, String title) {
        chatHistoryService.renameSession(sessionId, requireUserId(), title);
    }

    @Override
    public void pinSession(String sessionId, Boolean pinned) {
        chatHistoryService.pinSession(sessionId, requireUserId(), pinned);
    }

    @Override
    public void rateMessage(String sessionId, Long messageId, Integer feedback) {
        chatHistoryService.rateMessage(sessionId, messageId, requireUserId(), feedback);
    }

    private void validate(ChatRequest request) {
        if (request == null || StrUtil.isBlank(request.getQuery())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "问题内容不能为空");
        }
    }

    /**
     * 用户级对话限流：普通用户窗口内配额耗尽则拒绝，admin 不限。
     * 在参数校验之后、真正发起 LLM 调用之前执行，拦截最省（无效请求不计配额，避免被利用刷爆自己的配额）。
     */
    private void enforceRateLimit() {
        User user = UserContext.getUser();
        if (user != null && !chatRateLimitService.consume(user.getId(), user.getUserRole())) {
            log.warn("对话限流: userId={}", user.getId());
            throw new BusinessException(ErrorCode.CHAT_TOO_FREQUENT, "对话过于频繁，请稍后再试");
        }
    }

    /**
     * 注入受信用户身份与角色：userId / userRole 只能来自登录态（数据库用户 → ThreadLocal），
     * 前端传的 user_id / user_role 一律以当前登录用户为准，杜绝身份伪造（Python 侧据此选 LLM）
     */
    private void fillUserId(ChatRequest request) {
        User user = UserContext.getUser();
        if (user != null) {
            request.setUserId(String.valueOf(user.getId()));
            request.setUserRole(user.getUserRole());
        }
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

    /**
     * 知识库读取越权防护：/chat 与 /chat/stream 会把 knowledge_id 透传给 Python Agent 检索。
     * 转发前必须先按「作者 / 协作者 / 收藏者 / 公开库」校验当前用户可查看该知识库：
     * 无权时 getViewableKnowledge 抛 NO_AUTH，知识库不存在抛 NOT_FOUND，一律拒绝不发起检索。
     */
    private void checkKnowledgeAccess(String knowledgeId) {
        if (StrUtil.isBlank(knowledgeId)) {
            return;
        }
        Long id;
        try {
            id = Long.valueOf(knowledgeId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库参数错误");
        }
        knowledgeService.getViewableKnowledge(id);
    }

    /**
     * ChatRequest → Python Agent JSON（@JsonProperty snake_case 契约；
     * 调用前须 fillUserId 注入受信身份，序列化即携带 user_id/user_role）
     */
    private String toAgentJson(ChatRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "对话请求序列化失败");
        }
    }
}
