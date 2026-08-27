package com.agent.rag.config;

import cn.hutool.crypto.digest.BCrypt;
import com.agent.rag.entity.User;
import com.agent.rag.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminInitializer 单测：admin 已存在不重置；不存在时用 ADMIN_PASSWORD 创建；
 * 未配置则生成强随机密码。仓库内不再出现管理员凭据。
 *
 * @author pulinsenz
 */
@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserMapper userMapper;

    private AdminInitializer initializer(String password) {
        AdminInitializer init = new AdminInitializer();
        ReflectionTestUtils.setField(init, "userMapper", userMapper);
        ReflectionTestUtils.setField(init, "initialPassword", password);
        return init;
    }

    @Test
    void adminExists_doesNothing() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        initializer("whatever").run(null);
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void adminMissing_createsWithConfiguredPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        initializer("S3cureAdminPass!").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User admin = captor.getValue();
        assertEquals("admin", admin.getUserAccount());
        assertEquals("管理员", admin.getUserName());
        assertEquals("admin", admin.getUserRole());
        assertTrue(BCrypt.checkpw("S3cureAdminPass!", admin.getUserPassword()),
                "密码应以 BCrypt 落库且与 ADMIN_PASSWORD 一致");
    }

    @Test
    void adminMissing_generatesStrongRandomWhenNoPassword() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        initializer("").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User admin = captor.getValue();
        assertEquals("admin", admin.getUserAccount());
        // 未配置 ADMIN_PASSWORD 时也应落一个合法 BCrypt 哈希（随机强密码）
        assertTrue(admin.getUserPassword().startsWith("$2a$10$"), "密码必须是 BCrypt 哈希");
        assertTrue(admin.getUserPassword().length() >= 60, "BCrypt 哈希不应为空");
    }
}
