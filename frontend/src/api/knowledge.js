import request from './request'
import { trackKnowledgeChange } from '../utils/tabRefresh'

export const listKnowledgeQuestions = (id) => request.get(`/knowledge/${id}/questions`)
export const saveKnowledgeQuestions = (id, questions) => request.put(`/knowledge/${id}/questions`, { questions })

/** 创建知识库 */
export const createKnowledge = (data) => trackKnowledgeChange(request.post('/knowledge/create', data), 'list')

/** 更新知识库信息（名称/封面/简介/是否公开），仅作者 */
export const updateKnowledge = (data) => trackKnowledgeChange(request.post('/knowledge/update', data), 'list')

/** 我的知识库列表（我拥有的 + 我收藏的） */
export const listMyKnowledge = () => request.get('/knowledge/list')

/** 公开知识库列表（isPublic=1 且未删除），keyword 可选 */
export const listPublicKnowledge = (keyword) => request.get('/knowledge/public/list', { params: { keyword } })

/** 知识库详情 */
export const getKnowledge = (id) => request.get(`/knowledge/${id}`)

/** 收藏知识库（仅他人公开库） */
export const favoriteKnowledge = (id) => trackKnowledgeChange(request.post(`/knowledge/${id}/favorite`), 'list')

/** 取消收藏知识库 */
export const unfavoriteKnowledge = (id) => trackKnowledgeChange(request.delete(`/knowledge/${id}/favorite`), 'list')

/** 复制知识库：复制者为新作者，返回新知识库 id */
export const copyKnowledge = (id) => trackKnowledgeChange(request.post(`/knowledge/${id}/copy`), 'list')

/** 上传封面图片，返回可访问 URL */
export const uploadCover = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/knowledge/cover', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 协作者列表（仅作者） */
export const listMembers = (id) => request.get(`/knowledge/${id}/members`)

/** 邀请协作者（仅作者，按 userId 或 userAccount） */
export const addMember = (id, data) => request.post(`/knowledge/${id}/members`, data)

/** 移除协作者（仅作者） */
export const removeMember = (id, userId) => request.delete(`/knowledge/${id}/members/${userId}`)

/** 删除知识库（含文档） */
export const deleteKnowledge = (id) => trackKnowledgeChange(request.delete(`/knowledge/${id}`), 'list', 'docs', 'wiki')

/** 上传文档（multipart，字段名 file）：提交向量化任务，返回 taskId 供轮询 /task/{taskId} */
export const uploadDoc = (id, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return trackKnowledgeChange(request.post(`/knowledge/${id}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }), 'list', 'docs')
}

/** 知识库文档列表（deleted 过滤：null=全部/0=正常/1=已删除；category：null/all=全部, vectorized=已入库, unvectorized=未入库） */
export const listDocs = (id, deleted, category) => request.get(`/knowledge/${id}/docs`, { params: { deleted, category } })

/** 文档内容文件（原始文件，inline 预览），用于「打开」 */
export const getDocFile = (id, docId) => request.get(`/knowledge/${id}/docs/${docId}/file`, { responseType: 'blob' })

/** 文档内容文件（原始文件，attachment 下载），用于「下载」 */
export const downloadDocFile = (id, docId) => request.get(`/knowledge/${id}/docs/${docId}/download`, { responseType: 'blob' })

/** 文档切片详情（文本列表），用于「向量详情」 */
export const getDocChunks = (id, docId) => request.get(`/knowledge/${id}/docs/${docId}/chunks`)

export const testKnowledgeRetrieval = (id, query) =>
  request.post(`/knowledge/${id}/retrieval-test`, { query })

/** 恢复用户自己删除的文档（管理员删除的拒绝），返回 taskId 供轮询 */
export const restoreDoc = (id, docId) => trackKnowledgeChange(request.post(`/knowledge/${id}/docs/${docId}/restore`), 'list', 'docs')

/** 文档强制/重新入库（SKIPPED 强制向量化、FAILED/PENDING/REMOVED 重试）：返回 taskId 供轮询 */
export const reVectorizeDoc = (id, docId) => trackKnowledgeChange(request.post(`/knowledge/${id}/docs/${docId}/revectorize`), 'docs')

/** 移除入库：删除文档向量（保留文档记录），状态置为未入库 */
export const removeDocVector = (id, docId) => trackKnowledgeChange(request.post(`/knowledge/${id}/docs/${docId}/remove-vector`), 'docs')

/** 删除单个文档（逻辑删除 + 删向量，用户可自恢复） */
export const deleteDoc = (id, docId) => trackKnowledgeChange(request.delete(`/knowledge/${id}/docs/${docId}`), 'list', 'docs')

/** 彻底删除单个文档（物理删除记录 + 删本地文件 + 删向量，不可恢复） */
export const purgeDoc = (id, docId) => trackKnowledgeChange(request.delete(`/knowledge/${id}/docs/${docId}/purge`), 'list', 'docs')

/** 批量移除入库：删除所选文档向量（保留文档记录），返回处理数量 */
export const batchRemoveVector = (id, docIds) => trackKnowledgeChange(request.post(`/knowledge/${id}/docs/batch-remove-vector`, { docIds }), 'docs')

/** 批量删除文档（逻辑删除 + 删各文档向量），返回删除数量 */
export const batchDeleteDocs = (id, docIds) => trackKnowledgeChange(request.post(`/knowledge/${id}/docs/batch-delete`, { docIds }), 'list', 'docs')

/** 批量入库：为所选文档提交向量化任务（已入库/已删除自动跳过），返回 taskId 列表 */
export const batchVectorizeDoc = (id, docIds) => trackKnowledgeChange(request.post(`/knowledge/${id}/docs/batch-vectorize`, { docIds }), 'docs')

/** 长任务状态查询（上传向量化后轮询） */
export const getTask = (taskId) => request.get(`/task/${taskId}`)

// ---------- AI Wiki ----------

/** 生成（或重新生成）知识库 Wiki，返回 taskId 供轮询 /task/{taskId} */
export const generateWiki = (id) => request.post(`/knowledge/${id}/wiki/generate`)

/** Wiki 页面列表（不含正文），按 sortOrder 升序；未生成时为空数组 */
export const listWikiPages = (id) => request.get(`/knowledge/${id}/wiki`)

/** Wiki 页面详情（含 Markdown 正文） */
export const getWikiPage = (id, pageId) => request.get(`/knowledge/${id}/wiki/${pageId}`)

/** 手动编辑 Wiki 页面（标题/正文） */
export const updateWikiPage = (id, pageId, data) => trackKnowledgeChange(request.put(`/knowledge/${id}/wiki/${pageId}`, data), 'wiki')

/** 删除整个 Wiki（清向量 + 逻辑删页面） */
export const deleteWiki = (id) => trackKnowledgeChange(request.delete(`/knowledge/${id}/wiki`), 'wiki')

/** 消息中心：邀请列表 */
export const listKnowledgeInvitations = () => request.get('/knowledge/invitations')

/** 消息中心：标记邀请已读 */
export const readKnowledgeInvitation = (id) => request.post(`/knowledge/invitations/${id}/read`)

/** 消息中心：接受邀请 */
export const acceptKnowledgeInvitation = (id) => trackKnowledgeChange(request.post(`/knowledge/invitations/${id}/accept`), 'list')

/** 消息中心：拒绝邀请 */
export const rejectKnowledgeInvitation = (id) => request.post(`/knowledge/invitations/${id}/reject`)
