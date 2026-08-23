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

/** 知识库文档列表 */
export const listDocs = (id) => request.get(`/knowledge/${id}/docs`)

/** 长任务状态查询（上传向量化后轮询） */
export const getTask = (taskId) => request.get(`/task/${taskId}`)
