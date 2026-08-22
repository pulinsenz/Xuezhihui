package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.common.annotation.RequireRole;
import com.agent.rag.dto.req.UpdateUserRoleRequest;
import com.agent.rag.dto.req.UserQueryRequest;
import com.agent.rag.dto.resp.AdminUserVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.service.AdminService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员接口（全部需要 admin 角色，由 @RequireRole 注解在拦截器校验）
 *
 * @author pulinsenz
 */
@RestController
@RequestMapping("/admin")
@RequireRole("admin")
public class AdminController {

    @Resource
    private AdminService adminService;

    /**
     * 用户分页列表
     */
    @GetMapping("/user/list")
    public Result<Page<AdminUserVO>> listUsers(UserQueryRequest request) {
        return Result.success(adminService.listUsers(request));
    }

    /**
     * 修改用户角色
     */
    @PutMapping("/user/{id}/role")
    public Result<Boolean> updateRole(@PathVariable Long id, @RequestBody UpdateUserRoleRequest request) {
        adminService.updateUserRole(id, request);
        return Result.success(true);
    }

    /**
     * 删除用户（清理其登录 token，强制下线）
     */
    @DeleteMapping("/user/{id}")
    public Result<Boolean> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return Result.success(true);
    }

    /**
     * 恢复已删除用户
     */
    @PutMapping("/user/{id}/restore")
    public Result<Boolean> restoreUser(@PathVariable Long id) {
        adminService.restoreUser(id);
        return Result.success(true);
    }

    /**
     * 全局知识库列表
     */
    @GetMapping("/knowledge/list")
    public Result<Page<KnowledgeVO>> listKnowledge(UserQueryRequest request) {
        return Result.success(adminService.listAllKnowledge(request));
    }
}
