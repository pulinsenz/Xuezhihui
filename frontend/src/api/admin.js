import request from './request'

/** 用户分页列表（keyword / pageNum / pageSize） */
export const listUsers = (params) => request.get('/admin/user/list', { params })

/** 修改用户角色 */
export const updateUserRole = (id, data) => request.put(`/admin/user/${id}/role`, data)

/** 删除用户 */
export const deleteUser = (id) => request.delete(`/admin/user/${id}`)

/** 恢复已删除用户 */
export const restoreUser = (id) => request.put(`/admin/user/${id}/restore`)

/** 全局知识库分页列表（deleted 过滤：null=全部/0=正常/1=已删除） */
export const listAllKnowledge = (params) => request.get('/admin/knowledge/list', { params })

/** 知识库详情（管理员视角，可查看任意/已删除知识库） */
export const adminGetKnowledge = (id) => request.get(`/admin/knowledge/${id}`)

/** 知识库文档分页列表（deleted 过滤：null=全部/0=正常/1=已删除） */
export const listAllDocs = (id, params) => request.get(`/admin/knowledge/${id}/docs`, { params })

/** 删除知识库（逻辑删除 + 删向量），返回 true */
export const adminDeleteKnowledge = (id) => request.delete(`/admin/knowledge/${id}`)

/** 恢复知识库（取消删除 + 全部文档重新向量化），返回 taskId 数组 */
export const restoreKnowledge = (id) => request.put(`/admin/knowledge/${id}/restore`)

/** 删除知识库内单个文档（逻辑删除 + 删向量） */
export const adminDeleteDoc = (knowledgeId, docId) => request.delete(`/admin/knowledge/${knowledgeId}/docs/${docId}`)

/** 恢复已删除文档（取消删除 + 重新向量化），返回 taskId */
export const restoreDoc = (knowledgeId, docId) => request.put(`/admin/knowledge/${knowledgeId}/docs/${docId}/restore`)

/** 文档重新入库（重新向量化，仅 FAILED/PENDING），返回 taskId */
export const reVectorizeDoc = (knowledgeId, docId) => request.post(`/admin/knowledge/${knowledgeId}/docs/${docId}/revectorize`)
