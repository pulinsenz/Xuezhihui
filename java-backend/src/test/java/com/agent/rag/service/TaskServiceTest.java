package com.agent.rag.service;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.VectorStatus;
import com.agent.rag.dto.req.VectorizeCallbackRequest;
import com.agent.rag.dto.resp.TaskVO;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.service.impl.TaskServiceImpl;
import com.agent.rag.storage.FileStorageService;
import com.agent.rag.util.UserContext;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 长任务服务单元测试（Mockito，不连 Redis/DB）
 * <p>
 * 覆盖：发布任务（状态 Hash + 队列 push）、状态查询归属校验、worker 回调回写文档状态。
 *
 * @author pulinsenz
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ListOperations<String, String> listOps;
    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;
    @Mock
    private FileStorageService fileStorageService;

    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskServiceImpl();
        ReflectionTestUtils.setField(taskService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(taskService, "knowledgeDocMapper", knowledgeDocMapper);
        ReflectionTestUtils.setField(taskService, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(taskService, "queueVectorize", "xzh:task:vectorize");
        // lenient：不同测试只用其中一个 mock，宽松处理未使用 stub
        lenient().when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
        lenient().when(stringRedisTemplate.opsForList()).thenReturn(listOps);

        User user = new User();
        user.setId(1L);
        UserContext.setUser(user);
    }
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---------- 发布任务 ----------

    @Test
    void publishVectorize_writesStatusAndPushesQueue() {
        String taskId = taskService.publishVectorize(5L, 100L, "/data/files/course.txt", "course.txt");

        assertNotNull(taskId, "应生成任务 id");
        // 状态 Hash：PENDING + 归属用户
        ArgumentCaptor<Map<String, String>> statusCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("xzh:task:" + taskId), statusCaptor.capture());
        assertEquals("PENDING", statusCaptor.getValue().get("status"));
        assertEquals("1", statusCaptor.getValue().get("user_id"));
        // 过期时间
        verify(stringRedisTemplate).expire(eq("xzh:task:" + taskId), any());
        // 入队：file_url 经 presignedUrl 转换后传给 Python worker
        verify(fileStorageService).presignedUrl("/data/files/course.txt", 7200);
        verify(listOps).leftPush(eq("xzh:task:vectorize"), anyString());
    }

    @Test
    void publishVectorize_localPath_presignedUrlReturnsAsIs() {
        // 本地路径：presignedUrl 原样透传（worker 直读共享卷），消息里仍为绝对路径
        when(fileStorageService.presignedUrl("/data/files/course.txt", 7200))
                .thenReturn("/data/files/course.txt");

        String taskId = taskService.publishVectorize(5L, 100L, "/data/files/course.txt", "course.txt");

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOps).leftPush(eq("xzh:task:vectorize"), msgCaptor.capture());
        JSONObject msg = JSONUtil.parseObj(msgCaptor.getValue());
        assertEquals("/data/files/course.txt", msg.getStr("file_url"));
        assertNotNull(taskId);
    }

    @Test
    void publishVectorize_cosUrl_messageCarriesSignedUrl() {
        // COS 私有对象：消息里应带临时签名 URL，Python worker 免凭证即可拉取
        when(fileStorageService.presignedUrl(
                "https://picture-1391878614.cos.ap-guangzhou.myqcloud.com/doc/202608/100/a.md", 7200))
                .thenReturn("https://picture-1391878614.cos.ap-guangzhou.myqcloud.com/doc/202608/100/a.md?q-sign-algorithm=sha1");

        String taskId = taskService.publishVectorize(5L, 100L,
                "https://picture-1391878614.cos.ap-guangzhou.myqcloud.com/doc/202608/100/a.md", "a.md");

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOps).leftPush(eq("xzh:task:vectorize"), msgCaptor.capture());
        JSONObject msg = JSONUtil.parseObj(msgCaptor.getValue());
        assertTrue(msg.getStr("file_url").startsWith("https://picture-1391878614.cos.ap-guangzhou.myqcloud.com/doc/202608/100/a.md?q-sign-algorithm="),
                "COS 文档的任务消息应带签名 URL: " + msg.getStr("file_url"));
        assertNotNull(taskId);
    }

    // ---------- 查询任务状态 ----------

    @Test
    void getTask_returnsOwnedTask() {
        when(hashOps.entries(anyString())).thenReturn(Map.<Object, Object>of(
                "user_id", "1", "type", "vectorize", "status", "PROCESSING", "create_time", "2026-08-23T00:00:00"));

        TaskVO vo = taskService.getTask("t1");
        assertEquals("PROCESSING", vo.getStatus());
        assertEquals("vectorize", vo.getType());
        assertEquals("t1", vo.getTaskId());
    }

    @Test
    void getTask_notFound_throwsNotFound() {
        when(hashOps.entries(anyString())).thenReturn(Map.of());
        BusinessException e = assertThrows(BusinessException.class, () -> taskService.getTask("t1"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void getTask_otherUserTask_forbidden() {
        // 任务归属用户 2，当前登录用户 1 → 无权查看（防止枚举他人任务）
        when(hashOps.entries(anyString())).thenReturn(Map.<Object, Object>of("user_id", "2"));
        BusinessException e = assertThrows(BusinessException.class, () -> taskService.getTask("t1"));
        assertEquals(ErrorCode.NO_AUTH.getCode(), e.getCode());
    }

    // ---------- worker 回调回写 ----------

    @Test
    void completeVectorize_updatesDocStatusToSuccess() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(100L);
        // 回调 doc_id 是字符串（Long→String 全局序列化），selectById 用 String 匹配
        when(knowledgeDocMapper.selectById("100")).thenReturn(doc);

        VectorizeCallbackRequest req = new VectorizeCallbackRequest();
        req.setDocId("100");
        req.setStatus(VectorStatus.SUCCESS.name());
        taskService.completeVectorize(req);

        ArgumentCaptor<KnowledgeDoc> captor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(knowledgeDocMapper).updateById(captor.capture());
        assertEquals(VectorStatus.SUCCESS.name(), captor.getValue().getVectorStatus());
    }

    @Test
    void completeVectorize_failureRecordsErrorMsg() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(100L);
        when(knowledgeDocMapper.selectById("100")).thenReturn(doc);

        VectorizeCallbackRequest req = new VectorizeCallbackRequest();
        req.setDocId("100");
        req.setStatus(VectorStatus.FAILED.name());
        req.setErrorMsg("解析失败");
        taskService.completeVectorize(req);

        ArgumentCaptor<KnowledgeDoc> captor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(knowledgeDocMapper).updateById(captor.capture());
        assertEquals(VectorStatus.FAILED.name(), captor.getValue().getVectorStatus());
        assertEquals("解析失败", captor.getValue().getErrorMsg());
    }

    @Test
    void completeVectorize_docMissing_ignored() {
        when(knowledgeDocMapper.selectById("100")).thenReturn(null);
        VectorizeCallbackRequest req = new VectorizeCallbackRequest();
        req.setDocId("100");
        req.setStatus(VectorStatus.SUCCESS.name());
        // 文档不存在不抛错，只是忽略回写
        taskService.completeVectorize(req);
        verify(knowledgeDocMapper, never()).updateById(any(KnowledgeDoc.class));
    }

    @Test
    void completeVectorize_blankParams_throwsParamsError() {
        VectorizeCallbackRequest req = new VectorizeCallbackRequest();
        BusinessException e = assertThrows(BusinessException.class, () -> taskService.completeVectorize(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        assertTrue(e.getMessage() != null);
    }
}
