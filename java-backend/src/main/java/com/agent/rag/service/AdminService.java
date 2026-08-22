package com.agent.rag.service;

import com.agent.rag.dto.req.UpdateUserRoleRequest;
import com.agent.rag.dto.req.UserQueryRequest;
import com.agent.rag.dto.resp.AdminUserVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 管理员服务
 *
 * @author pulinsenz
 */
public interface AdminService {

    /**
     * 用户分页列表（关键词搜索账号/昵称，deleted 过滤：null=全部/0=正常/1=已删除）
     */
    Page<AdminUserVO> listUsers(UserQueryRequest request);

    /**
     * 修改用户角色（校验角色合法、不能操作自己）
     */
    void updateUserRole(Long userId, UpdateUserRoleRequest request);

    /**
     * 删除用户：逻辑删除 + 清理其 Redis 白名单 token 强制下线
     */
    void deleteUser(Long userId);

    /**
     * 恢复已删除用户（isDelete 置 0）
     */
    void restoreUser(Long userId);

    /**
     * 全局知识库分页列表（管理员视角）
     */
    Page<KnowledgeVO> listAllKnowledge(UserQueryRequest request);
}
