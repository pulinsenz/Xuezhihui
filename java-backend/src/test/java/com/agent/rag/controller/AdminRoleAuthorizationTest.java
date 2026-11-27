package com.agent.rag.controller;

import com.agent.rag.common.RoleConstant;
import com.agent.rag.config.JwtProperties;
import com.agent.rag.entity.User;
import com.agent.rag.exception.GlobalExceptionHandler;
import com.agent.rag.interceptor.LoginInterceptor;
import com.agent.rag.mapper.UserMapper;
import com.agent.rag.service.AdminService;
import com.agent.rag.util.JwtUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class AdminRoleAuthorizationTest {

    @Mock private AdminService adminService;
    @Mock private UserMapper userMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> redisValues;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRedisPrefix("test:");
        LoginInterceptor interceptor = new LoginInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(interceptor, "jwtProperties", jwtProperties);
        ReflectionTestUtils.setField(interceptor, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(interceptor, "userMapper", userMapper);

        AdminController controller = new AdminController();
        ReflectionTestUtils.setField(controller, "adminService", adminService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptor)
                .build();
    }

    private void loginAs(String role) {
        User user = new User();
        user.setId(1L);
        user.setUserRole(role);
        when(jwtUtil.resolveToken(any())).thenReturn("token");
        when(jwtUtil.verify("token")).thenReturn(true);
        when(redis.opsForValue()).thenReturn(redisValues);
        when(redisValues.get("test:token")).thenReturn("1");
        when(userMapper.selectById("1")).thenReturn(user);
    }

    private ResultActions changeRole() throws Exception {
        return mvc.perform(put("/admin/user/2/role")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userRole\":\"admin\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {RoleConstant.ADMIN, RoleConstant.USER})
    void roleUpdateDeniedForNonSuperAdmin(String role) throws Exception {
        loginAs(role);
        changeRole().andExpect(jsonPath("$.code").value(40101));
        verifyNoInteractions(adminService);
    }

    @Test
    void roleUpdateAllowedForSuperAdmin() throws Exception {
        loginAs(RoleConstant.SUPER_ADMIN);
        changeRole().andExpect(jsonPath("$.code").value(0));
        verify(adminService).updateUserRole(eq(2L), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN})
    void bothAdministratorRolesRetainReadAccess(String role) throws Exception {
        loginAs(role);
        when(adminService.listUsers(any())).thenReturn(new Page<>());
        mvc.perform(get("/admin/user/list").header("Authorization", "Bearer token"))
                .andExpect(jsonPath("$.code").value(0));
    }
}
