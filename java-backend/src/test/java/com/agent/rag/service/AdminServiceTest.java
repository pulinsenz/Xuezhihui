package com.agent.rag.service;

import com.agent.rag.common.ErrorCode;
import com.agent.rag.common.RoleConstant;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.dto.req.UpdateUserRoleRequest;
import com.agent.rag.dto.req.UserQueryRequest;
import com.agent.rag.dto.resp.AdminUserVO;
import com.agent.rag.entity.User;
import com.agent.rag.exception.BusinessException;
import com.agent.rag.mapper.KnowledgeDocMapper;
import com.agent.rag.mapper.KnowledgeMapper;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.impl.AdminServiceImpl;
import com.agent.rag.util.UserContext;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private Cursor<String> cursor;
    @Mock
    private ValueOperations<String, String> valueOperations;

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
        ReflectionTestUtils.setField(adminService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(adminService, "jwtProperties", jwtProperties);

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
        when(userMapper.selectPage(any(), any())).thenReturn(page);

        Page<AdminUserVO> result = adminService.listUsers(new UserQueryRequest());

        assertEquals(1, result.getTotal());
        assertEquals("bob", result.getRecords().get(0).getUserAccount());
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
}
