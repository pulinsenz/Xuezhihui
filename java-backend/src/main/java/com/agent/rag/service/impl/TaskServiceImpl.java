package com.agent.rag.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.VectorStatus;
import com.agent.rag.dto.req.VectorizeCallbackRequest;
import com.agent.rag.dto.resp.TaskVO;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.service.TaskService;
import com.agent.rag.storage.FileStorageService;
import com.agent.rag.util.UserContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 长任务服务实现：Redis List 消息队列 + 任务状态 Redis Hash
 * <p>
 * 消息流：Java 提交（LPUSH）→ Python worker 消费（BRPOP）执行 → 完成后回调 Java 回写 MySQL。
 * 任务状态存 Redis Hash（key=xzh:task:{id}），前端通过 TaskController 轮询，规避 HTTP 超时。
 *
 * @author pulinsenz
 */
@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

    private static final String TASK_KEY_PREFIX = "xzh:task:";
    private static final String STATUS_PENDING = "PENDING";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    @Resource
    private FileStorageService fileStorageService;

    @Value("${app.task.queue-vectorize}")
    private String queueVectorize;

    @Override
    public String publishVectorize(Long knowledgeId, Long docId, String fileUrl, String name) {
        String taskId = IdUtil.getSnowflakeNextIdStr();
        User user = UserContext.getUser();
        String userId = user == null ? null : String.valueOf(user.getId());

        // 1. 任务消息（Python worker 消费）：file_url 为 COS 私有对象时带临时签名（2h），
        //    python worker 无需 COS 凭证即可拉取；本地路径原样透传（worker 直读共享卷）
        Map<String, Object> message = new HashMap<>();
        message.put("task_id", taskId);
        message.put("knowledge_id", String.valueOf(knowledgeId));
        message.put("doc_id", String.valueOf(docId));
        message.put("file_url", fileStorageService.presignedUrl(fileUrl, 7200));
        message.put("name", name);

        // 2. 任务状态（Redis Hash，前端轮询）
        Map<String, String> status = new HashMap<>();
        status.put("type", "vectorize");
        status.put("status", STATUS_PENDING);
        status.put("create_time", LocalDateTime.now().toString());
        if (userId != null) {
            status.put("user_id", userId);
        }
        stringRedisTemplate.opsForHash().putAll(TASK_KEY_PREFIX + taskId, status);
        // 任务状态 24h 过期，防止 Redis 无限膨胀
        stringRedisTemplate.expire(TASK_KEY_PREFIX + taskId, Duration.ofHours(24));

        // 3. 入队：Python worker BRPOP 消费
        stringRedisTemplate.opsForList().leftPush(queueVectorize, JSONUtil.toJsonStr(message));
        log.info("向量化任务已提交: taskId={}, docId={}, queue={}", taskId, docId, queueVectorize);
        return taskId;
    }

    @Override
    public TaskVO getTask(String taskId) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(TASK_KEY_PREFIX + taskId);
        if (entries.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在或已过期");
        }
        // 归属校验：只能查当前登录用户的任务，防止枚举他人任务
        String taskUserId = (String) entries.get("user_id");
        User user = UserContext.getUser();
        if (taskUserId != null && (user == null || !taskUserId.equals(String.valueOf(user.getId())))) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权查看该任务");
        }
        TaskVO vo = new TaskVO();
        vo.setTaskId(taskId);
        vo.setType((String) entries.get("type"));
        vo.setStatus((String) entries.get("status"));
        vo.setMessage((String) entries.get("message"));
        vo.setCreateTime((String) entries.get("create_time"));
        vo.setFinishTime((String) entries.get("finish_time"));
        return vo;
    }

    @Override
    public void completeVectorize(VectorizeCallbackRequest request) {
        if (request == null || request.getDocId() == null || request.getStatus() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "回调参数错误");
        }
        KnowledgeDoc doc = knowledgeDocMapper.selectById(request.getDocId());
        if (doc == null) {
            log.warn("向量化回调更新失败，文档不存在: docId={}", request.getDocId());
            return;
        }
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(doc.getId());
        update.setVectorStatus(request.getStatus());
        update.setErrorMsg(request.getErrorMsg());
        knowledgeDocMapper.updateById(update);
        log.info("向量化任务回写完成: docId={}, status={}", request.getDocId(), request.getStatus());
    }
}
