package com.agent.rag.controller;

import com.agent.rag.common.Result;
import com.agent.rag.common.annotation.RequireRole;
import com.agent.rag.dto.req.BatchDocRequest;
import com.agent.rag.dto.req.UpdateUserRoleRequest;
import com.agent.rag.dto.req.UserQueryRequest;
import com.agent.rag.dto.resp.AdminUserVO;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.agent.rag.service.AdminService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     * 全局知识库列表（deleted 过滤：null=全部/0=正常/1=已删除）
     */
    @GetMapping("/knowledge/list")
    public Result<Page<KnowledgeVO>> listKnowledge(UserQueryRequest request) {
        return Result.success(adminService.listAllKnowledge(request));
    }

    /**
     * 知识库详情（任意用户/已删除均可查看，供管理员打开知识库）
     */
    @GetMapping("/knowledge/{id}")
    public Result<KnowledgeVO> knowledgeDetail(@PathVariable Long id) {
        return Result.success(adminService.getKnowledgeDetail(id));
    }

    /**
     * 知识库文档列表（deleted 过滤：null=全部/0=正常/1=已删除）
     */
    @GetMapping("/knowledge/{id}/docs")
    public Result<Page<KnowledgeDocVO>> knowledgeDocs(@PathVariable Long id, UserQueryRequest request) {
        return Result.success(adminService.listAllDocs(id, request));
    }

    /**
     * 删除知识库：逻辑删除 + 全部文档逻辑删除 + 删除向量
     */
    @DeleteMapping("/knowledge/{id}")
    public Result<Boolean> deleteKnowledge(@PathVariable Long id) {
        adminService.deleteKnowledgeByAdmin(id);
        return Result.success(true);
    }

    /**
     * 恢复已删除知识库：取消删除 + 全部文档取消删除 + 逐个重新向量化
     */
    @PutMapping("/knowledge/{id}/restore")
    public Result<List<String>> restoreKnowledge(@PathVariable Long id) {
        return Result.success(adminService.restoreKnowledge(id));
    }

    /**
     * 删除知识库内单个文档：逻辑删除 + 删除该文档向量
     */
    @DeleteMapping("/knowledge/{id}/docs/{docId}")
    public Result<Boolean> deleteDoc(@PathVariable Long id, @PathVariable Long docId) {
        adminService.deleteDocByAdmin(id, docId);
        return Result.success(true);
    }

    /**
     * 恢复已删除文档：取消删除 + 重新向量化
     */
    @PutMapping("/knowledge/{id}/docs/{docId}/restore")
    public Result<String> restoreDoc(@PathVariable Long id, @PathVariable Long docId) {
        return Result.success(adminService.restoreDoc(id, docId));
    }

    /**
     * 重新入库（重新向量化）：仅支持 FAILED/PENDING/SKIPPED/REMOVED 且未删除的文档
     */
    @PostMapping("/knowledge/{id}/docs/{docId}/revectorize")
    public Result<String> reVectorize(@PathVariable Long id, @PathVariable Long docId) {
        return Result.success(adminService.reVectorize(id, docId));
    }

    /**
     * 移除入库：删除文档向量（保留文档记录），状态置为未入库
     */
    @PostMapping("/knowledge/{id}/docs/{docId}/remove-vector")
    public Result<Boolean> removeDocVector(@PathVariable Long id, @PathVariable Long docId) {
        adminService.removeDocVector(id, docId);
        return Result.success(true);
    }

    /**
     * 批量移除入库：删除所选文档向量（保留文档记录），返回处理数量
     */
    @PostMapping("/knowledge/{id}/docs/batch-remove-vector")
    public Result<Integer> batchRemoveDocVector(@PathVariable Long id, @RequestBody BatchDocRequest request) {
        return Result.success(adminService.batchRemoveDocVector(id, request.getDocIds()));
    }

    /**
     * 批量删除文档：逻辑删除 + 删除各文档向量，返回删除数量
     */
    @PostMapping("/knowledge/{id}/docs/batch-delete")
    public Result<Integer> batchDeleteDocs(@PathVariable Long id, @RequestBody BatchDocRequest request) {
        return Result.success(adminService.batchDeleteDocs(id, request.getDocIds()));
    }
}
