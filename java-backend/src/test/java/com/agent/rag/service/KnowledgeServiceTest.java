package com.agent.rag.service;

import com.agent.rag.client.PythonAgentClient;
import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.VectorStatus;
import com.agent.rag.dto.req.DeleteVectorRequest;
import com.agent.rag.dto.req.KnowledgeCreateRequest;
import com.agent.rag.dto.req.MemberInviteRequest;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.entity.Knowledge;
import com.agent.rag.entity.KnowledgeDoc;
import com.agent.rag.entity.KnowledgeInvitation;
import com.agent.rag.entity.KnowledgeMember;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.ForbiddenFileHashMapper;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeFavoriteMapper;
import com.agent.rag.mapper.KnowledgeInvitationMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.agent.rag.mapper.KnowledgeMemberMapper;
import com.agent.rag.mapper.UserMapper;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private KnowledgeFavoriteMapper knowledgeFavoriteMapper;
    @Mock
    private KnowledgeMemberMapper knowledgeMemberMapper;
    @Mock
    private KnowledgeInvitationMapper knowledgeInvitationMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ForbiddenFileHashMapper forbiddenFileHashMapper;
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
        ReflectionTestUtils.setField(knowledgeService, "knowledgeFavoriteMapper", knowledgeFavoriteMapper);
        ReflectionTestUtils.setField(knowledgeService, "knowledgeMemberMapper", knowledgeMemberMapper);
        ReflectionTestUtils.setField(knowledgeService, "knowledgeInvitationMapper", knowledgeInvitationMapper);
        ReflectionTestUtils.setField(knowledgeService, "userMapper", userMapper);
        ReflectionTestUtils.setField(knowledgeService, "forbiddenFileHashMapper", forbiddenFileHashMapper);
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
        when(knowledgeFavoriteMapper.selectList(any())).thenReturn(List.of());
        when(knowledgeMemberMapper.selectList(any())).thenReturn(List.of());
        when(knowledgeDocMapper.selectCount(any())).thenReturn(3L);

        List<KnowledgeVO> list = knowledgeService.listMyKnowledge();
        assertEquals(1, list.size());
        assertEquals(3L, list.get(0).getDocCount());
    }

    @Test
    void listMyKnowledge_includesMemberKnowledge() {
        Knowledge owned = ownedKnowledge(1L);
        owned.setName("我的库");
        owned.setCreateTime(LocalDateTime.now().minusDays(1));
        Knowledge memberKb = new Knowledge();
        memberKb.setId(2L);
        memberKb.setUserId(99L);
        memberKb.setName("协作库");
        memberKb.setCreateTime(LocalDateTime.now());
        KnowledgeMember member = new KnowledgeMember();
        member.setKnowledgeId(2L);
        member.setUserId(1L);

        when(knowledgeMapper.selectList(any())).thenReturn(List.of(owned));
        when(knowledgeMemberMapper.selectList(any())).thenReturn(List.of(member));
        when(knowledgeFavoriteMapper.selectList(any())).thenReturn(List.of());
        when(knowledgeMapper.selectBatchIds(any())).thenReturn(List.of(memberKb));
        when(knowledgeDocMapper.selectCount(any())).thenReturn(0L);

        List<KnowledgeVO> list = knowledgeService.listMyKnowledge();

        assertEquals(2, list.size());
        assertEquals("协作库", list.get(0).getName());
        assertEquals(false, list.get(0).getIsOwner());
    }

    @Test
    void listDocs_returnsDocVos() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(100L);
        doc.setKnowledgeId(5L);
        doc.setName("a.txt");
        doc.setVectorStatus(VectorStatus.PENDING.name());
        when(knowledgeDocMapper.selectDocsByFilter(eq(5L), any(), any())).thenReturn(List.of(doc));

        assertEquals(1, knowledgeService.listDocs(5L, 0, "all").size());
    }

    // ---------- 批量入库 ----------

    @Test
    void batchVectorize_submitsTasksForNonSuccessDocs() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        KnowledgeDoc success = new KnowledgeDoc();
        success.setId(100L);
        success.setKnowledgeId(5L);
        success.setVectorStatus(VectorStatus.SUCCESS.name());
        KnowledgeDoc failed = new KnowledgeDoc();
        failed.setId(101L);
        failed.setKnowledgeId(5L);
        failed.setVectorStatus(VectorStatus.FAILED.name());
        failed.setFileUrl("/f/1.txt");
        failed.setName("1.txt");
        when(knowledgeDocMapper.selectById(100L)).thenReturn(success);
        when(knowledgeDocMapper.selectById(101L)).thenReturn(failed);
        when(taskService.publishVectorize(eq(5L), eq(101L), eq("/f/1.txt"), eq("1.txt"))).thenReturn("t1");

        List<String> taskIds = knowledgeService.batchVectorize(5L, List.of(100L, 101L));

        assertEquals(List.of("t1"), taskIds, "已入库文档应跳过，只提交未入库任务");
        verify(taskService, never()).publishVectorize(eq(5L), eq(100L), any(), any());
    }

    @Test
    void batchVectorize_emptyIds_returnsEmpty() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));

        assertEquals(List.of(), knowledgeService.batchVectorize(5L, List.of()));
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

    // ---------- 上传：管理员封禁哈希禁止 ----------

    @Test
    void uploadDoc_bannedHash_rejected() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        when(forbiddenFileHashMapper.existsByHash(any())).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> knowledgeService.uploadDoc(5L, sampleFile()));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
        // 命中黑名单应阻止落盘，store 不应被调用
        verify(fileStorageService, never()).store(any(), any());
    }

    @Test
    void uploadDoc_legacyAdminDeletedStillRejected() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        // 黑名单表无行，但同库存在历史 admin 删除记录（旧版管理员删除）→ 仍应拦截
        when(knowledgeDocMapper.countAdminDeletedByHash(eq(5L), any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> knowledgeService.uploadDoc(5L, sampleFile()));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }

    // ---------- 邀请消息 ----------

    @Test
    void addMember_sendsInvitationInsteadOfDirectMemberInsert() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        User target = new User();
        target.setId(2L);
        target.setUserAccount("alice");
        target.setUserName("Alice");
        when(userMapper.selectOne(any())).thenReturn(target);
        when(knowledgeMemberMapper.selectCount(any())).thenReturn(0L);
        when(knowledgeInvitationMapper.selectOne(any())).thenReturn(null);
        when(knowledgeInvitationMapper.insert(any(KnowledgeInvitation.class))).thenReturn(1);

        MemberInviteRequest request = new MemberInviteRequest();
        request.setUserAccount("alice");
        knowledgeService.addMember(5L, request);

        verify(knowledgeInvitationMapper).insert(any(KnowledgeInvitation.class));
        verify(knowledgeMemberMapper, never()).insertIgnore(any(), any());
    }

    @Test
    void acceptInvitation_addsMemberAndMarksHandled() {
        User invitee = new User();
        invitee.setId(2L);
        UserContext.setUser(invitee);

        KnowledgeInvitation invitation = new KnowledgeInvitation();
        invitation.setId(100L);
        invitation.setKnowledgeId(5L);
        invitation.setInviterId(1L);
        invitation.setTargetUserId(2L);
        invitation.setStatus("PENDING");
        when(knowledgeInvitationMapper.selectById(100L)).thenReturn(invitation);
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        when(knowledgeInvitationMapper.updateById(any(KnowledgeInvitation.class))).thenReturn(1);
        when(knowledgeMemberMapper.insertIgnore(5L, 2L)).thenReturn(1);

        Long knowledgeId = knowledgeService.acceptInvitation(100L);

        assertEquals(5L, knowledgeId);
        verify(knowledgeMemberMapper).insertIgnore(5L, 2L);
        verify(knowledgeInvitationMapper).updateById(any(KnowledgeInvitation.class));
    }

    // ---------- 彻底删除 ----------

    private KnowledgeDoc ownedDoc(Long id, Long knowledgeId, String fileUrl) {
        KnowledgeDoc d = new KnowledgeDoc();
        d.setId(id);
        d.setKnowledgeId(knowledgeId);
        d.setFileUrl(fileUrl);
        return d;
    }

    @Test
    void purgeDoc_success_logicalDeleteAndRemovesVector() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        when(knowledgeDocMapper.selectAnyById(100L)).thenReturn(ownedDoc(100L, 5L, "/data/files/a.txt"));

        knowledgeService.purgeDoc(5L, 100L);

        // 彻底删除 = 逻辑删除 + 标记 purged（记录保留、不删本地文件）
        verify(knowledgeDocMapper).markPurged(100L);
        // 删除向量（Python Agent，按 knowledgeId+docId）
        ArgumentCaptor<DeleteVectorRequest> captor = ArgumentCaptor.forClass(DeleteVectorRequest.class);
        verify(pythonAgentClient).deleteKnowledge(captor.capture());
        assertEquals("5", captor.getValue().getKnowledgeId());
        assertEquals("100", captor.getValue().getDocId());
    }

    @Test
    void purgeDoc_deletedDoc_stillAllowed() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        KnowledgeDoc doc = ownedDoc(100L, 5L, "/data/files/a.txt");
        doc.setIsDelete(1); // 已删除文档同样可彻底删除（selectById 查不到，须走 selectAnyById）
        when(knowledgeDocMapper.selectAnyById(100L)).thenReturn(doc);

        knowledgeService.purgeDoc(5L, 100L);

        verify(knowledgeDocMapper).markPurged(100L);
    }

    @Test
    void purgeDoc_otherKnowledge_throws() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        when(knowledgeDocMapper.selectAnyById(100L)).thenReturn(ownedDoc(100L, 99L, "/data/files/a.txt"));

        BusinessException e = assertThrows(BusinessException.class, () -> knowledgeService.purgeDoc(5L, 100L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), e.getCode());
        verify(knowledgeDocMapper, never()).markPurged(any());
    }

    // ---------- 恢复：管理员封禁哈希禁止 ----------

    @Test
    void restoreDoc_bannedHash_rejected() {
        when(knowledgeMapper.selectById(5L)).thenReturn(ownedKnowledge(5L));
        KnowledgeDoc doc = ownedDoc(100L, 5L, "/data/files/a.txt");
        doc.setIsDelete(1);
        doc.setDeleteSource("user"); // 用户自删，但内容哈希后被管理员封禁 → 仍不可恢复
        doc.setFileHash("banned-hash");
        when(knowledgeDocMapper.selectAnyById(100L)).thenReturn(doc);
        when(forbiddenFileHashMapper.existsByHash("banned-hash")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class, () -> knowledgeService.restoreDoc(5L, 100L));
        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), e.getCode());
    }
}
