import request from './request'

/** 创建知识库 */
export const createKnowledge = (data) => request.post('/knowledge/create', data)

/** 我的知识库列表 */
export const listMyKnowledge = () => request.get('/knowledge/list')

/** 知识库详情 */
export const getKnowledge = (id) => request.get(`/knowledge/${id}`)

/** 删除知识库（含文档） */
export const deleteKnowledge = (id) => request.delete(`/knowledge/${id}`)

/** 上传文档（multipart，字段名 file）：提交向量化任务，返回 taskId 供轮询 /task/{taskId} */
export const uploadDoc = (id, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/knowledge/${id}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 知识库文档列表（deleted 过滤：null=全部/0=正常/1=已删除；category：null/all=全部, vectorized=已入库, unvectorized=未入库） */
export const listDocs = (id, deleted, category) => request.get(`/knowledge/${id}/docs`, { params: { deleted, category } })

/** 恢复用户自己删除的文档（管理员删除的拒绝），返回 taskId 供轮询 */
export const restoreDoc = (id, docId) => request.post(`/knowledge/${id}/docs/${docId}/restore`)

/** 文档强制/重新入库（SKIPPED 强制向量化、FAILED/PENDING/REMOVED 重试）：返回 taskId 供轮询 */
export const reVectorizeDoc = (id, docId) => request.post(`/knowledge/${id}/docs/${docId}/revectorize`)

/** 移除入库：删除文档向量（保留文档记录），状态置为未入库 */
export const removeDocVector = (id, docId) => request.post(`/knowledge/${id}/docs/${docId}/remove-vector`)

/** 删除单个文档（逻辑删除 + 删向量，用户可自恢复） */
export const deleteDoc = (id, docId) => request.delete(`/knowledge/${id}/docs/${docId}`)

/** 彻底删除单个文档（物理删除记录 + 删本地文件 + 删向量，不可恢复） */
export const purgeDoc = (id, docId) => request.delete(`/knowledge/${id}/docs/${docId}/purge`)

/** 批量移除入库：删除所选文档向量（保留文档记录），返回处理数量 */
export const batchRemoveVector = (id, docIds) => request.post(`/knowledge/${id}/docs/batch-remove-vector`, { docIds })

/** 批量删除文档（逻辑删除 + 删各文档向量），返回删除数量 */
export const batchDeleteDocs = (id, docIds) => request.post(`/knowledge/${id}/docs/batch-delete`, { docIds })

/** 批量入库：为所选文档提交向量化任务（已入库/已删除自动跳过），返回 taskId 列表 */
export const batchVectorizeDoc = (id, docIds) => request.post(`/knowledge/${id}/docs/batch-vectorize`, { docIds })

/** 长任务状态查询（上传向量化后轮询） */
export const getTask = (taskId) => request.get(`/task/${taskId}`)
