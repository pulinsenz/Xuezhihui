package com.agent.rag.service;

import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.RoleConstant;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.req.UpdateUserRoleRequest;
import com.agent.rag.dto.req.UserQueryRequest;
import com.agent.rag.dto.resp.AdminUserVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.ForbiddenFileHashMapper;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.impl.AdminServiceImpl;
import com.agent.rag.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminService 单元测试（Mockito mock 依赖，不连数据库）
 *
 * @author pulinsenz
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private KnowledgeMapper knowledgeMapper;
    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;
    @Mock
    private ForbiddenFileHashMapper forbiddenFileHashMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private Cursor<String> cursor;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private PythonAgentClient pythonAgentClient;
    @Mock
    private TaskService taskService;

    private JwtProperties jwtProperties;
    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setRedisPrefix("xzh:login:");
        adminService = new AdminServiceImpl();
        ReflectionTestUtils.setField(adminService, "userMapper", userMapper);
        ReflectionTestUtils.setField(adminService, "knowledgeMapper", knowledgeMapper);
        ReflectionTestUtils.setField(adminService, "knowledgeDocMapper", knowledgeDocMapper);
        ReflectionTestUtils.setField(adminService, "forbiddenFileHashMapper", forbiddenFileHashMapper);
        ReflectionTestUtils.setField(adminService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(adminService, "jwtProperties", jwtProperties);
        ReflectionTestUtils.setField(adminService, "pythonAgentClient", pythonAgentClient);
        ReflectionTestUtils.setField(adminService, "taskService", taskService);

        User admin = new User();
        admin.setId(1L);
        admin.setUserRole(RoleConstant.ADMIN);
        UserContext.setUser(admin);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ---------- 用户列表 ----------

    @Test
    void listUsers_returnsPage() {
        User u = new User();
        u.setId(2L);
        u.setUserAccount("bob");
        u.setUserRole(RoleConstant.USER);
        Page<User> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(u));
        when(userMapper.selectUserPage(any(Page.class), any(), any())).thenReturn(page);

        Page<AdminUserVO> result = adminService.listUsers(new UserQueryRequest());

        assertEquals(1, result.getTotal());
        assertEquals("bob", result.getRecords().get(0).getUserAccount());
    }

    @Test
    void listUsers_passesDeletedFilter() {
        when(userMapper.selectUserPage(any(Page.class), any(), eq(1))).thenReturn(new Page<>(1, 10, 0));

        UserQueryRequest req = new UserQueryRequest();
        req.setDeleted(1); // 只查已删除
        adminService.listUsers(req);

        verify(userMapper).selectUserPage(any(Page.class), any(), eq(1));
    }

    // ---------- 修改角色 ----------

    @Test
    void updateUserRole_invalidRole_throws() {
        UpdateUserRoleRequest req = new UpdateUserRoleRequest();
        req.setUserRole("superadmin");
        BusinessException e = assertThrows(BusinessException.class,
                () -> adminService.updateUserRole(2L, req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void updateUserRole_self_throws() {
        UpdateUserRoleRequest req = new UpdateUserRoleRequest();
        req.setUserRole(RoleConstant.USER);
        BusinessException e = assertThrows(BusinessException.class,
                () -> adminService.updateUserRole(1L, req));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void updateUserRole_notExist_throws() {
        when(userMapper.selectById(2L)).thenReturn(null);
        UpdateUserRoleRequest req = new UpdateUserRoleRequest();
        req.setUserRole(RoleConstant.ADMIN);
        BusinessException e = assertThrows(BusinessException.class,
                () -> adminService.updateUserRole(2L, req));
        assertEquals(ErrorCode.USER_NOT_EXIST.getCode(), e.getCode());
    }

    @Test
    void updateUserRole_success() {
        User target = new User();
        target.setId(2L);
        target.setUserRole(RoleConstant.USER);
        when(userMapper.selectById(2L)).thenReturn(target);

        UpdateUserRoleRequest req = new UpdateUserRoleRequest();
        req.setUserRole(RoleConstant.ADMIN);
        adminService.updateUserRole(2L, req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(RoleConstant.ADMIN, captor.getValue().getUserRole());
    }

    // ---------- 删除用户 ----------

    @Test
    void deleteUser_self_throws() {
        BusinessException e = assertThrows(BusinessException.class, () -> adminService.deleteUser(1L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void deleteUser_notExist_throws() {
        when(userMapper.selectById(99L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> adminService.deleteUser(99L));
        assertEquals(ErrorCode.USER_NOT_EXIST.getCode(), e.getCode());
    }

    @Test
    void deleteUser_success_evictsOnlyOwnTokens() {
        User target = new User();
        target.setId(7L);
        target.setUserRole(RoleConstant.USER);
        when(userMapper.selectById(7L)).thenReturn(target);

        // SCAN 返回两个 key，一个属于该用户(id=7)，一个属于别人
        when(stringRedisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn("xzh:login:tokenA", "xzh:login:tokenB");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("xzh:login:tokenA")).thenReturn("7");
        when(valueOperations.get("xzh:login:tokenB")).thenReturn("99");

        adminService.deleteUser(7L);

        verify(userMapper).deleteById(7L);
        verify(stringRedisTemplate).delete("xzh:login:tokenA");
        verify(stringRedisTemplate, never()).delete("xzh:login:tokenB");
    }

    // ---------- 恢复用户 ----------

    @Test
    void restoreUser_success() {
        when(userMapper.restoreDeleted(7L)).thenReturn(1);
        adminService.restoreUser(7L);
        verify(userMapper).restoreDeleted(7L);
    }

    @Test
    void restoreUser_notDeleted_throws() {
        when(userMapper.restoreDeleted(7L)).thenReturn(0);
        BusinessException e = assertThrows(BusinessException.class, () -> adminService.restoreUser(7L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    // ---------- 知识库列表 ----------

    @Test
    void listAllKnowledge_passesDeletedFilter_andCountsAllDocs() {
        Knowledge kb = new Knowledge();
        kb.setId(10L);
        kb.setName("高数");
        kb.setIsDelete(1);
        Page<Knowledge> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(kb));
        when(knowledgeMapper.selectKnowledgePage(any(Page.class), any(), eq(1))).thenReturn(page);
        when(knowledgeDocMapper.countAll(10L)).thenReturn(3L);

        UserQueryRequest req = new UserQueryRequest();
        req.setDeleted(1);
        Page<KnowledgeVO> result = adminService.listAllKnowledge(req);

        verify(knowledgeMapper).selectKnowledgePage(any(Page.class), any(), eq(1));
        assertEquals(1, result.getTotal());
        assertEquals(Integer.valueOf(1), result.getRecords().get(0).getIsDelete());
        assertEquals(Long.valueOf(3), result.getRecords().get(0).getDocCount());
    }

    // ---------- 知识库详情 / 文档列表 ----------

    @Test
    void getKnowledgeDetail_notExist_throws() {
        when(knowledgeMapper.selectAnyById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> adminService.getKnowledgeDetail(9L));
    }

    @Test
    void listAllDocs_notExist_throws() {
        when(knowledgeMapper.selectAnyById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> adminService.listAllDocs(9L, new UserQueryRequest()));
    }

    @Test
    void listAllDocs_passesDeletedFilter() {
        Knowledge kb = new Knowledge();
        kb.setId(10L);
        when(knowledgeMapper.selectAnyById(10L)).thenReturn(kb);
        when(knowledgeDocMapper.selectDocPage(any(Page.class), eq(10L), any(), eq(0)))
                .thenReturn(new Page<>(1, 10, 0));

        UserQueryRequest req = new UserQueryRequest();
        req.setDeleted(0);
        adminService.listAllDocs(10L, req);

        verify(knowledgeDocMapper).selectDocPage(any(Page.class), eq(10L), any(), eq(0));
    }

    // ---------- 删除知识库 ----------

    @Test
    void deleteKnowledgeByAdmin_notExist_throws() {
        when(knowledgeMapper.selectAnyById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> adminService.deleteKnowledgeByAdmin(9L));
    }

    @Test
    void deleteKnowledgeByAdmin_success() {
        Knowledge kb = new Knowledge();
        kb.setId(10L);
        when(knowledgeMapper.selectAnyById(10L)).thenReturn(kb);

        adminService.deleteKnowledgeByAdmin(10L);

        verify(knowledgeMapper).deleteById(10L);
        verify(knowledgeDocMapper).markDeletedByKnowledgeId(eq(10L), eq("admin"));
        ArgumentCaptor<DeleteVectorRequest> captor = ArgumentCaptor.forClass(DeleteVectorRequest.class);
        verify(pythonAgentClient).deleteKnowledge(captor.capture());
        assertEquals("10", captor.getValue().getKnowledgeId());
        assertEquals(null, captor.getValue().getDocId());
    }

    @Test
    void deleteKnowledgeByAdmin_pythonFail_degrades() {
        Knowledge kb = new Knowledge();
        kb.setId(10L);
        when(knowledgeMapper.selectAnyById(10L)).thenReturn(kb);
        org.mockito.Mockito.doThrow(new RuntimeException("down"))
                .when(pythonAgentClient).deleteKnowledge(any());

        adminService.deleteKnowledgeByAdmin(10L); // Python 失败不阻断主流程
        verify(knowledgeMapper).deleteById(10L);
    }

    // ---------- 恢复知识库 ----------

    @Test
    void restoreKnowledge_notDeleted_throws() {
        Knowledge kb = new Knowledge();
        kb.setId(10L);
        kb.setIsDelete(0);
        when(knowledgeMapper.selectAnyById(10L)).thenReturn(kb);
        BusinessException e = assertThrows(BusinessException.class, () -> adminService.restoreKnowledge(10L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void restoreKnowledge_success_publishesPerDoc() {
        Knowledge kb = new Knowledge();
        kb.setId(10L);
        kb.setIsDelete(1);
        when(knowledgeMapper.selectAnyById(10L)).thenReturn(kb);
        when(knowledgeMapper.restoreDeleted(10L)).thenReturn(1);

        KnowledgeDoc doc1 = new KnowledgeDoc();
        doc1.setId(101L);
        doc1.setFileUrl("/f/1.txt");
        doc1.setName("1.txt");
        KnowledgeDoc doc2 = new KnowledgeDoc();
        doc2.setId(102L);
        doc2.setFileUrl("/f/2.txt");
        doc2.setName("2.txt");
        when(knowledgeDocMapper.selectAllByKnowledgeId(10L)).thenReturn(List.of(doc1, doc2));
        when(taskService.publishVectorize(eq(10L), eq(101L), eq("/f/1.txt"), eq("1.txt"))).thenReturn("t1");
        when(taskService.publishVectorize(eq(10L), eq(102L), eq("/f/2.txt"), eq("2.txt"))).thenReturn("t2");

        List<String> taskIds = adminService.restoreKnowledge(10L);

        // 逐条恢复（跳过被封禁哈希的文档），不再批量恢复
        verify(knowledgeDocMapper).restoreDeleted(101L);
        verify(knowledgeDocMapper).restoreDeleted(102L);
        verify(knowledgeDocMapper, never()).restoreDeletedByKnowledgeId(10L);
        assertEquals(List.of("t1", "t2"), taskIds);
    }

    @Test
    void restoreKnowledge_emptyDocs_returnsEmpty() {
        Knowledge kb = new Knowledge();
        kb.setId(10L);
        kb.setIsDelete(1);
        when(knowledgeMapper.selectAnyById(10L)).thenReturn(kb);
        when(knowledgeMapper.restoreDeleted(10L)).thenReturn(1);
        when(knowledgeDocMapper.selectAllByKnowledgeId(10L)).thenReturn(List.of());

        List<String> taskIds = adminService.restoreKnowledge(10L);
        assertEquals(List.of(), taskIds);
    }

    // ---------- 删除文档 ----------

    @Test
    void deleteDocByAdmin_mismatch_throws() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(99L); // 不属于该知识库
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        BusinessException e = assertThrows(BusinessException.class, () -> adminService.deleteDocByAdmin(10L, 101L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    void deleteDocByAdmin_success() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        adminService.deleteDocByAdmin(10L, 101L);

        verify(knowledgeDocMapper).markDeleted(eq(101L), eq("admin"));
        ArgumentCaptor<DeleteVectorRequest> captor = ArgumentCaptor.forClass(DeleteVectorRequest.class);
        verify(pythonAgentClient).deleteKnowledge(captor.capture());
        assertEquals("101", captor.getValue().getDocId());
    }

    // ---------- 恢复文档 ----------

    @Test
    void restoreDoc_notDeleted_throws() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        doc.setIsDelete(0);
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        BusinessException e = assertThrows(BusinessException.class, () -> adminService.restoreDoc(10L, 101L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void restoreDoc_success_returnsTaskId() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        doc.setIsDelete(1);
        doc.setFileUrl("/f/1.txt");
        doc.setName("1.txt");
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);
        when(knowledgeDocMapper.restoreDeleted(101L)).thenReturn(1);
        when(taskService.publishVectorize(eq(10L), eq(101L), eq("/f/1.txt"), eq("1.txt"))).thenReturn("t1");

        String taskId = adminService.restoreDoc(10L, 101L);
        assertEquals("t1", taskId);
        verify(knowledgeDocMapper).restoreDeleted(101L);
    }

    // ---------- 重新入库 ----------

    @Test
    void reVectorize_deletedDoc_throws() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        doc.setIsDelete(1);
        doc.setVectorStatus("FAILED");
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        BusinessException e = assertThrows(BusinessException.class, () -> adminService.reVectorize(10L, 101L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    @Test
    void reVectorize_successDoc_throws() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        doc.setIsDelete(0);
        doc.setVectorStatus("SUCCESS");
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        assertThrows(BusinessException.class, () -> adminService.reVectorize(10L, 101L));
    }

    @Test
    void reVectorize_failedDoc_success() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        doc.setIsDelete(0);
        doc.setVectorStatus("FAILED");
        doc.setFileUrl("/f/1.txt");
        doc.setName("1.txt");
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);
        when(taskService.publishVectorize(eq(10L), eq(101L), eq("/f/1.txt"), eq("1.txt"))).thenReturn("t1");

        String taskId = adminService.reVectorize(10L, 101L);
        assertEquals("t1", taskId);
        verify(knowledgeDocMapper, never()).restoreDeleted(any());
    }

    // ---------- 移除入库 ----------

    @Test
    void removeDocVector_success_marksRemoved() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        doc.setVectorStatus("SUCCESS");
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        adminService.removeDocVector(10L, 101L);

        ArgumentCaptor<KnowledgeDoc> captor = ArgumentCaptor.forClass(KnowledgeDoc.class);
        verify(knowledgeDocMapper).updateById(captor.capture());
        assertEquals("REMOVED", captor.getValue().getVectorStatus());
        verify(pythonAgentClient).deleteKnowledge(any(DeleteVectorRequest.class));
    }

    @Test
    void removeDocVector_notVectorized_throws() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        doc.setVectorStatus("PENDING");
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        BusinessException e = assertThrows(BusinessException.class, () -> adminService.removeDocVector(10L, 101L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    // ---------- 批量移除入库 / 批量删除 ----------

    @Test
    void batchRemoveDocVector_countsSuccessDocsOnly() {
        KnowledgeDoc success = new KnowledgeDoc();
        success.setId(101L);
        success.setKnowledgeId(10L);
        success.setVectorStatus("SUCCESS");
        KnowledgeDoc pending = new KnowledgeDoc();
        pending.setId(102L);
        pending.setKnowledgeId(10L);
        pending.setVectorStatus("PENDING");
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(success);
        when(knowledgeDocMapper.selectAnyById(102L)).thenReturn(pending);

        int count = adminService.batchRemoveDocVector(10L, List.of(101L, 102L));

        assertEquals(1, count, "只处理已入库的文档");
        verify(pythonAgentClient, times(1)).deleteKnowledge(any(DeleteVectorRequest.class));
    }

    @Test
    void batchDeleteDocs_deletesAllSelected() {
        KnowledgeDoc d1 = new KnowledgeDoc();
        d1.setId(101L);
        d1.setKnowledgeId(10L);
        d1.setIsDelete(0);
        KnowledgeDoc d2 = new KnowledgeDoc();
        d2.setId(102L);
        d2.setKnowledgeId(10L);
        d2.setIsDelete(0);
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(d1);
        when(knowledgeDocMapper.selectAnyById(102L)).thenReturn(d2);

        int count = adminService.batchDeleteDocs(10L, List.of(101L, 102L));

        assertEquals(2, count, "两条都应删除");
        // count 已证明 deleteById 各执行一次；此处验证向量删除也各执行一次
        verify(pythonAgentClient, times(2)).deleteKnowledge(any(DeleteVectorRequest.class));
    }

    // ---------- 管理员删除：哈希封禁 + 级联删除相同文件 ----------

    private KnowledgeDoc docWithHash(Long id, Long knowledgeId, String hash, String fileUrl) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(id);
        doc.setKnowledgeId(knowledgeId);
        doc.setFileHash(hash);
        doc.setFileUrl(fileUrl);
        doc.setIsDelete(0);
        return doc;
    }

    @Test
    void deleteDocByAdmin_cascadesSameHashGlobally() {
        KnowledgeDoc target = docWithHash(101L, 10L, "hashA", "/f/1.txt");
        KnowledgeDoc other = docWithHash(202L, 20L, "hashA", "/f/2.txt"); // 其他用户/知识库的同内容文档
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(target);
        when(knowledgeDocMapper.selectActiveByHash("hashA")).thenReturn(List.of(target, other));

        adminService.deleteDocByAdmin(10L, 101L);

        // 记录哈希到黑名单
        verify(forbiddenFileHashMapper).insertIgnore("hashA");
        // 两条同哈希文档均逻辑删除（来源=admin），本地文件保留不删除
        verify(knowledgeDocMapper).markDeleted(101L, "admin");
        verify(knowledgeDocMapper).markDeleted(202L, "admin");
        // 向量删除必须用各自 knowledgeId/docId（跨库不误删、不留孤儿向量）
        ArgumentCaptor<DeleteVectorRequest> captor = ArgumentCaptor.forClass(DeleteVectorRequest.class);
        verify(pythonAgentClient, times(2)).deleteKnowledge(captor.capture());
        List<DeleteVectorRequest> requests = captor.getAllValues();
        assertTrue(requests.stream().anyMatch(r -> "10".equals(r.getKnowledgeId()) && "101".equals(r.getDocId())),
                "目标文档按自己的库/文档 id 删向量");
        assertTrue(requests.stream().anyMatch(r -> "20".equals(r.getKnowledgeId()) && "202".equals(r.getDocId())),
                "级联文档按自己的库/文档 id 删向量");
    }

    @Test
    void deleteDocByAdmin_nullHash_legacyBehavior() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(101L);
        doc.setKnowledgeId(10L);
        doc.setIsDelete(0);
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        adminService.deleteDocByAdmin(10L, 101L);

        // 无哈希（历史数据）：仅删目标，不封禁、不级联
        verify(knowledgeDocMapper).markDeleted(101L, "admin");
        verify(forbiddenFileHashMapper, never()).insertIgnore(any());
        verify(knowledgeDocMapper, never()).selectActiveByHash(any());
    }

    @Test
    void deleteDocByAdmin_alreadyDeleted_throws() {
        KnowledgeDoc doc = docWithHash(101L, 10L, "hashA", "/f/1.txt");
        doc.setIsDelete(1);
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);

        BusinessException e = assertThrows(BusinessException.class, () -> adminService.deleteDocByAdmin(10L, 101L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        verify(knowledgeDocMapper, never()).markDeleted(any(), any());
    }

    @Test
    void batchDeleteDocs_cascadesDistinctHashes() {
        KnowledgeDoc d1 = docWithHash(101L, 10L, "hashA", "/f/1.txt");
        KnowledgeDoc d2 = docWithHash(102L, 10L, "hashB", "/f/2.txt");
        KnowledgeDoc d3 = docWithHash(103L, 10L, "hashA", "/f/3.txt"); // 与 d1 同哈希 → 去重
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(d1);
        when(knowledgeDocMapper.selectAnyById(102L)).thenReturn(d2);
        when(knowledgeDocMapper.selectAnyById(103L)).thenReturn(d3);
        when(knowledgeDocMapper.selectActiveByHash("hashA")).thenReturn(List.of(d1, d3));
        when(knowledgeDocMapper.selectActiveByHash("hashB")).thenReturn(List.of(d2));

        int count = adminService.batchDeleteDocs(10L, List.of(101L, 102L, 103L));

        assertEquals(2, count, "同哈希去重后按 2 个内容组处理");
        verify(forbiddenFileHashMapper).insertIgnore("hashA");
        verify(forbiddenFileHashMapper).insertIgnore("hashB");
        verify(knowledgeDocMapper).markDeleted(101L, "admin");
        verify(knowledgeDocMapper).markDeleted(102L, "admin");
        verify(knowledgeDocMapper).markDeleted(103L, "admin"); // 同哈希 d3 被级联删除
    }

    @Test
    void restoreDoc_bannedHash_rejected() {
        KnowledgeDoc doc = docWithHash(101L, 10L, "banned", "/f/1.txt");
        doc.setIsDelete(1);
        when(knowledgeDocMapper.selectAnyById(101L)).thenReturn(doc);
        when(forbiddenFileHashMapper.existsByHash("banned")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class, () -> adminService.restoreDoc(10L, 101L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        verify(knowledgeDocMapper, never()).restoreDeleted(any());
    }

    @Test
    void restoreKnowledge_skipsBannedDocs() {
        Knowledge kb = new Knowledge();
        kb.setId(10L);
        kb.setIsDelete(1);
        when(knowledgeMapper.selectAnyById(10L)).thenReturn(kb);
        when(knowledgeMapper.restoreDeleted(10L)).thenReturn(1);

        KnowledgeDoc banned = new KnowledgeDoc();
        banned.setId(101L);
        banned.setFileUrl("/f/banned.txt");
        banned.setName("banned.txt");
        banned.setFileHash("banned-hash");
        KnowledgeDoc ok = new KnowledgeDoc();
        ok.setId(102L);
        ok.setFileUrl("/f/ok.txt");
        ok.setName("ok.txt");
        when(knowledgeDocMapper.selectAllByKnowledgeId(10L)).thenReturn(List.of(banned, ok));
        when(forbiddenFileHashMapper.existsByHash("banned-hash")).thenReturn(true);
        when(taskService.publishVectorize(eq(10L), eq(102L), eq("/f/ok.txt"), eq("ok.txt"))).thenReturn("t2");

        List<String> taskIds = adminService.restoreKnowledge(10L);

        assertEquals(List.of("t2"), taskIds);
        verify(knowledgeDocMapper).restoreDeleted(102L);
        verify(knowledgeDocMapper, never()).restoreDeleted(101L); // 被封禁文档保持删除
    }
}
