package com.agent.rag.controller;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.Result;
import com.agent.rag.dto.resp.UserStatsVO;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.service.KnowledgeService;
import com.agent.rag.service.AgentEvidenceService;
import java.util.Map;
import com.agent.rag.util.InternalAuthUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部接口：供 Python Agent 工具回调获取业务数据。
 * <p>
 * 信任边界：Python Agent 不直接访问 MySQL，业务数据一律通过本接口回调获取。
 * 不走 JWT 拦截器（Python 无登录态），改由 X-Agent-Token 双向鉴权（与 app.agent.token 一致）。
 * user_id 必须是 Java 从 JWT 解析后透传的受信值，前端不可自行传参。
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/internal/agent")
public class AgentDataController {

    @Resource
    private KnowledgeService knowledgeService;

    @Resource
    private AgentEvidenceService agentEvidenceService;

    @Value("${app.agent.token:}")
    private String agentToken;

    /**
     * 用户业务数据统计（工具 Agent 回答「我的知识库/文档」类问题）
     */
    @GetMapping("/user-stats")
    public Result<UserStatsVO> userStats(@RequestParam("user_id") Long userId,
                                         @RequestHeader(value = "X-Agent-Token", required = false) String token) {
        if (!InternalAuthUtil.check(agentToken, token)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "内部接口未授权");
        }
        UserStatsVO vo = knowledgeService.getUserStats(userId);
        log.info("工具回调: userId={}, 知识库数={}, 文档数={}", userId, vo.getKnowledgeCount(), vo.getDocCount());
        return Result.success(vo);
    }

    /** 全库目录取证：复用现有知识库读取权限，无业务写入。 */
    @GetMapping("/knowledge-outline")
    public Result<Map<String, Object>> knowledgeOutline(
            @RequestParam("user_id") Long userId,
            @RequestParam("knowledge_id") Long knowledgeId,
            @RequestHeader(value = "X-Agent-Token", required = false) String token) {
        requireInternalToken(token);
        return Result.success(agentEvidenceService.knowledgeOutline(userId, knowledgeId));
    }

    /** 历史取证：校验会话所有权及引用来源的当前读取权限。 */
    @GetMapping("/conversation-evidence")
    public Result<Map<String, Object>> conversationEvidence(
            @RequestParam("user_id") Long userId,
            @RequestParam("session_id") String sessionId,
            @RequestHeader(value = "X-Agent-Token", required = false) String token) {
        requireInternalToken(token);
        return Result.success(agentEvidenceService.conversationEvidence(userId, sessionId));
    }

    private void requireInternalToken(String token) {
        if (!InternalAuthUtil.check(agentToken, token)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "内部接口未授权");
        }
    }
}
