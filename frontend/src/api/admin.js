import request from './request'

/** 用户分页列表（keyword / pageNum / pageSize） */
export const listUsers = (params) => request.get('/admin/user/list', { params })

/** 修改用户角色 */
export const updateUserRole = (id, data) => request.put(`/admin/user/${id}/role`, data)

/** 删除用户 */
export const deleteUser = (id) => request.delete(`/admin/user/${id}`)

/** 全局知识库分页列表 */
export const listAllKnowledge = (params) => request.get('/admin/knowledge/list', { params })
