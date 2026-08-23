package com.agent.rag.service;

import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.VectorStatus;
import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.agent.rag.service.impl.KnowledgeServiceImpl;
import com.agent.rag.storage.FileStorageService;
import com.agent.rag.util.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeService 单元测试（Mockito mock 依赖，不连数据库）
 *
 * @author pulinsenz
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private KnowledgeMapper knowledgeMapper;
    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private PythonAgentClient pythonAgentClient;
    @Mock
    private TaskService taskService;

    private KnowledgeServiceImpl knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = new KnowledgeServiceImpl();
        ReflectionTestUtils.setField(knowledgeService, "knowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(knowledgeService, "knowledgeDocMapper", knowledgeDocMapper);
        ReflectionTestUtils.setField(knowledgeService, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(knowledgeService, "pythonAgentClient", pythonAgentClient);
        ReflectionTestUtils.setField(knowledgeService, "taskService", taskService);

        User user = new User();
        user.setId(1L);
        UserContext.setUser(user);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private Knowledge ownedKnowledge(Long id) {
        Knowledge k = new Knowledge();
        k.setId(id);
        k.setUserId(1L);
        return k;
    }

    // ---------- 创建 ----------

    @Test
    void createKnowledge_blankName_throwsParamsError() {
        KnowledgeCreateRequest req = new KnowledgeCreateRequest();
        BusinessException e = assertThrows(BusinessException.class,
                () -> knowledgeService.createKnowledge(req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void createKnowledge_success_setsOwnerFromLoginUser() {
        when(knowledgeMapper.insert(any(Knowledge.class))).thenAnswer(invocation -> {
            Knowledge k = invocation.getArgument(0);
            k.setId(10L);
            return 1;
        });

        KnowledgeCreateRequest req = new KnowledgeCreateRequest();
        req.setName("计算机课程");
        Long id = knowledgeService.createKnowledge(req);

        assertEquals(10L, id);
        ArgumentCaptor<Knowledge> captor = ArgumentCaptor.forClass(Knowledge.class);
        verify(knowledgeMapper).insert(captor.capture());
        assertEquals("计算机课程", captor.getValue().getName());
        assertEquals(1L, captor.getValue().getUserId(), "所属人应取当前登录用户");
    }

    // ---------- 权限校验 ----------

    @Test
    void getOwnedKnowledge_notFound_throwsNotFound() {
        when(knowledgeMapper.selectById(99L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class,
                () -> knowledgeService.getOwnedKnowledge(99L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void getOwnedKnowledge_othersKnowledge_throwsNoAuth() {
        Knowledge k = new Knowledge();
        k.setId(5L);
        k.setUserId(2L); // 别人的
        when(knowledgeMapper.selectById(5L)).thenReturn(k);
        BusinessException e = assertThrows(BusinessException.class,
                () -> knowledgeService.getOwnedKnowledge(5L));
        assertEquals(ErrorCode.NO_AUTH.getCode(), e.getCode());
    }

    // ---------- 删除 ----------

    @Test
    void deleteKnowledge_deletesKnowledgeAndDocs() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        knowledgeService.deleteKnowledge(5L);
        verify(knowledgeMapper).deleteById(5L);
        verify(knowledgeDocMapper).delete(any());
    }

    // ---------- 列表 ----------

    @Test
    void listMyKnowledge_returnsDocCount() {
        Knowledge k1 = ownedKnowledge(1L);
        k1.setName("知识库A");
        when(knowledgeMapper.selectList(any())).thenReturn(List.of(k1));
        when(knowledgeDocMapper.selectCount(any())).thenReturn(3L);

        List<KnowledgeVO> list = knowledgeService.listMyKnowledge();
        assertEquals(1, list.size());
        assertEquals(3L, list.get(0).getDocCount());
    }

    @Test
    void listDocs_returnsDocVos() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(100L);
        doc.setKnowledgeId(5L);
        doc.setName("a.txt");
        doc.setVectorStatus(VectorStatus.PENDING.name());
        when(knowledgeDocMapper.selectDocsByFilter(eq(5L), any())).thenReturn(List.of(doc));

        assertEquals(1, knowledgeService.listDocs(5L, 0).size());
    }

    // ---------- 上传（提交向量化任务到消息队列） ----------

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "course.txt", "text/plain", "hello".getBytes());
    }

    @Test
    void uploadDoc_submitsVectorizeTaskToQueue() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        when(fileStorageService.store(any(), eq(1L))).thenReturn("/data/files/course.txt");
        when(knowledgeDocMapper.insert(any(KnowledgeDoc.class))).thenAnswer(invocation -> {
            KnowledgeDoc d = invocation.getArgument(0);
            d.setId(100L);
            return 1;
        });
        when(taskService.publishVectorize(eq(5L), eq(100L), eq("/data/files/course.txt"), any()))
                .thenReturn("task-1");

        String taskId = knowledgeService.uploadDoc(5L, sampleFile());

        assertEquals("task-1", taskId, "上传应返回任务 id 供前端轮询");
        // 文档初始状态为待向量化
        ArgumentCaptor<KnowledgeDoc> captor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(knowledgeDocMapper).insert(captor.capture());
        assertEquals(VectorStatus.PENDING.name(), captor.getValue().getVectorStatus());
    }
}
