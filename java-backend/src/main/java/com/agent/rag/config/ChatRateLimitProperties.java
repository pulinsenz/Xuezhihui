package com.agent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话限流配置（普通用户 LLM 调用额度，防脚本刷 token 薅 LLM 成本）
 * <p>
 * 管理员不限流。仅按"发起对话请求"计数（SSE 流式算 1 次），不按 token 数，
 * 足够挡住脚本化滥用，对正常用户无感。可按生产实际调参：
 * <ul>
 *   <li>max-count-per-window：窗口内最多对话次数</li>
 *   <li>window-minutes：固定窗口时长（分钟），窗口到点计数归零</li>
 * </ul>
 *
 * @author pulinsenz
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.chat")
public class ChatRateLimitProperties {

    /**
     * 单用户窗口内对话次数上限（普通用户；admin 不限）
     */
    private int maxCountPerWindow = 50;

    /**
     * 计数固定窗口（分钟）
     */
    private int windowMinutes = 30;
}
