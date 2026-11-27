package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.dto.req.ChatRequest;
import com.agent.rag.dto.req.RenameSessionRequest;
import com.agent.rag.dto.req.PinSessionRequest;
import com.agent.rag.dto.req.MessageFeedbackRequest;
import com.agent.rag.dto.resp.ChatResponse;
import com.agent.rag.dto.resp.HistoryMessageVO;
import com.agent.rag.dto.resp.SessionVO;
import com.agent.rag.service.ChatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 对话接口
 *
 * @author pulinsenz
 */
@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @PutMapping("/sessions/{sessionId}/pin")
    public Result<Boolean> pin(@PathVariable String sessionId, @RequestBody PinSessionRequest request) {
        chatService.pinSession(sessionId, request.getPinned());
        return Result.success(true);
    }

    @PutMapping("/sessions/{sessionId}/messages/{messageId}/feedback")
    public Result<Boolean> feedback(@PathVariable String sessionId, @PathVariable Long messageId,
                                    @RequestBody MessageFeedbackRequest request) {
        chatService.rateMessage(sessionId, messageId, request.getFeedback());
        return Result.success(true);
    }

    /**
     * 普通对话（非流式）
     */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        return Result.success(chatService.chat(request));
    }

    /**
     * SSE 流式对话：POST + JSON body（改自 GET query：用户提问全文不再落两级访问日志）
     * <p>
     * body 复用 ChatRequest（@JsonProperty snake_case，与 Python Agent / 前端契约一致）。
     * 事件格式：data: {"type":"token","content":"..."} / {"type":"done","answer":"...","sources":[...]} / {"type":"error",...}
     */
    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody ChatRequest request) {
        return chatService.stream(request);
    }

    /**
     * 当前用户的会话列表（对话历史侧栏）
     */
    @GetMapping("/sessions")
    public Result<List<SessionVO>> sessions() {
        return Result.success(chatService.listSessions());
    }

    /**
     * 会话完整历史（仅本人）
     */
    @GetMapping("/sessions/{sessionId}/history")
    public Result<List<HistoryMessageVO>> sessionHistory(@PathVariable String sessionId) {
        return Result.success(chatService.getSessionHistory(sessionId));
    }

    /**
     * 删除会话及其消息（仅本人）
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Boolean> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success(true);
    }

    /**
     * 重命名会话（仅本人）：只改标题，不改变最近活跃排序
     */
    @PutMapping("/sessions/{sessionId}/title")
    public Result<Boolean> renameSession(@PathVariable String sessionId,
                                         @RequestBody RenameSessionRequest request) {
        chatService.renameSession(sessionId, request == null ? null : request.getTitle());
        return Result.success(true);
    }
}
