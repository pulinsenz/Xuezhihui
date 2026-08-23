package com.agent.rag.service;

import com.agent.rag.dto.req.UpdateUserRoleRequest;
import com.agent.rag.dto.req.UserQueryRequest;
import com.agent.rag.dto.resp.AdminUserVO;
import com.agent.rag.dto.resp.KnowledgeDocVO;
import com.agent.rag.dto.resp.KnowledgeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

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
     * 全局知识库分页列表（管理员视角，deleted 过滤：null=全部/0=正常/1=已删除）
     */
    Page<KnowledgeVO> listAllKnowledge(UserQueryRequest request);

    /**
     * 知识库详情（不校验归属，管理员可查看任意/已删除知识库）
     */
    KnowledgeVO getKnowledgeDetail(Long knowledgeId);

    /**
     * 知识库文档分页列表（管理员视角，deleted 过滤：null=全部/0=正常/1=已删除）
     */
    Page<KnowledgeDocVO> listAllDocs(Long knowledgeId, UserQueryRequest request);

    /**
     * 删除知识库：逻辑删除 + 全部文档逻辑删除 + 删除向量（不校验归属）
     */
    void deleteKnowledgeByAdmin(Long knowledgeId);

    /**
     * 恢复已删除知识库：取消删除 + 全部文档取消删除 + 逐个文档重新向量化
     *
     * @return 每个文档重新入库的任务 id 列表
     */
    List<String> restoreKnowledge(Long knowledgeId);

    /**
     * 删除单个文档：逻辑删除 + 删除该文档向量（不校验归属）
     */
    void deleteDocByAdmin(Long knowledgeId, Long docId);

    /**
     * 恢复已删除文档：取消删除 + 重新向量化
     *
     * @return 重新入库的任务 id
     */
    String restoreDoc(Long knowledgeId, Long docId);

    /**
     * 重新入库（重新向量化）：仅支持 FAILED/PENDING 且未删除的文档，不改动删除状态
     *
     * @return 重新入库的任务 id
     */
    String reVectorize(Long knowledgeId, Long docId);
}
