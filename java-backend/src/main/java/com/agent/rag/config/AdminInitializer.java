package com.agent.rag.config;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.agent.rag.entity.User;
import com.agent.rag.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 初始管理员初始化器：应用启动时确保 admin 账号存在。
 * <p>
 * 密码来源（首次创建时生效）：
 * 1) 环境变量 ADMIN_PASSWORD（生产必配，BCrypt 落库，不出现在任何仓库文件）；
 * 2) 未配置则生成 32 位随机密码并 WARN 打印一次（提示部署者改用 ADMIN_PASSWORD）。
 * admin 已存在时不做任何改动（避免生产误重置导致管理员被锁定）。
 * <p>
 * 设计背景：管理员凭据必须由部署者持有。此前把 admin 的 BCrypt 哈希硬编码进 init.sql 并提交公开仓库，
 * 等于把可离线爆破的管理员哈希公开给攻击者——安全基线要求仓库内不再出现任何管理员凭据。
 *
 * @author pulinsenz
 */
@Slf4j
@Component
public class AdminInitializer implements ApplicationRunner {

    @Resource
    private UserMapper userMapper;

    @Value("${app.admin.initial-password:}")
    private String initialPassword;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long adminCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUserAccount, "admin"));
            if (adminCount != null && adminCount > 0) {
                log.info("admin 账号已存在，跳过初始创建（不改动现有密码）");
                return;
            }
            String password = StrUtil.isNotBlank(initialPassword)
                    ? initialPassword
                    : IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
            User admin = new User();
            admin.setUserAccount("admin");
            admin.setUserPassword(BCrypt.hashpw(password));
            admin.setUserName("管理员");
            admin.setUserRole("admin");
            admin.setEditTime(LocalDateTime.now());
            userMapper.insert(admin);
            if (StrUtil.isBlank(initialPassword)) {
                log.warn("未配置 ADMIN_PASSWORD，已为 admin 生成随机密码（仅本次日志可见，请保存并改用 ADMIN_PASSWORD 部署）: {}",
                        password);
            } else {
                log.info("初始管理员创建成功: admin（密码来自 ADMIN_PASSWORD）");
            }
        } catch (Exception e) {
            log.error("初始管理员创建失败，请检查数据库连接与表结构: {}", e.getMessage(), e);
        }
    }
}
