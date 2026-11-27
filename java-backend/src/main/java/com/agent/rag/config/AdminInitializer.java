package com.agent.rag.config;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.agent.rag.common.RoleConstant;
import com.agent.rag.entity.User;
import com.agent.rag.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 初始超级管理员初始化器：应用启动时创建 admin 或升级既有管理员。
 * <p>
 * 密码来源（首次创建时生效）：
 * 1) 环境变量 ADMIN_PASSWORD（生产必配，BCrypt 落库，不出现在任何仓库文件）；
 * 2) 未配置则生成 32 位随机密码并 WARN 打印一次（提示部署者改用 ADMIN_PASSWORD）。
 * 仅升级既有 admin 管理员的角色，不重置密码或修改其他资料。
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
            String account = RoleConstant.BUILT_IN_ADMIN_ACCOUNT;
            if (userMapper.upgradeBuiltInAdmin(account, RoleConstant.ADMIN, RoleConstant.SUPER_ADMIN) > 0) {
                log.info("已将内置 admin 升级为超级管理员（不改动现有密码）");
                return;
            }
            // 含已删除账号：避免重复插入，也不自动恢复或提权同名普通账号。
            if (userMapper.countByAccountIncludingDeleted(account) > 0) {
                log.info("admin 账号已存在，跳过初始创建");
                return;
            }
            String password = StrUtil.isNotBlank(initialPassword)
                    ? initialPassword
                    : IdUtil.fastSimpleUUID() + IdUtil.fastSimpleUUID();
            User admin = new User();
            admin.setUserAccount(account);
            admin.setUserPassword(BCrypt.hashpw(password));
            admin.setUserName("超级管理员");
            admin.setUserRole(RoleConstant.SUPER_ADMIN);
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
