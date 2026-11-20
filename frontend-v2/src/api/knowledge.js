import request from './request'

// ---- 知识库 ----
export const createKnowledge = (data) => request.post('/knowledge/create', data)
export const updateKnowledge = (data) => request.post('/knowledge/update', data)
export const listMyKnowledge = () => request.get('/knowledge/list')
export const listPublicKnowledge = (keyword) => request.get('/knowledge/public/list', { params: { keyword } })
export const getKnowledge = (id) => request.get(`/knowledge/${id}`)
export const favoriteKnowledge = (id) => request.post(`/knowledge/${id}/favorite`)
export const unfavoriteKnowledge = (id) => request.delete(`/knowledge/${id}/favorite`)
export const copyKnowledge = (id) => request.post(`/knowledge/${id}/copy`)
export const deleteKnowledge = (id) => request.delete(`/knowledge/${id}`)
export const listMembers = (id) => request.get(`/knowledge/${id}/members`)
export const addMember = (id, data) => request.post(`/knowledge/${id}/members`, data)
export const removeMember = (id, userId) => request.delete(`/knowledge/${id}/members/${userId}`)

export const uploadCover = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/knowledge/cover', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}

// ---- 文档 ----
export const uploadDoc = (id, file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post(`/knowledge/${id}/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
export const listDocs = (id, deleted, category) =>
  request.get(`/knowledge/${id}/docs`, { params: { deleted, category } })
export const deleteDoc = (id, docId) => request.delete(`/knowledge/${id}/docs/${docId}`)
export const reVectorizeDoc = (id, docId) => request.post(`/knowledge/${id}/docs/${docId}/revectorize`)
export const removeDocVector = (id, docId) => request.post(`/knowledge/${id}/docs/${docId}/remove-vector`)
export const getTask = (taskId) => request.get(`/task/${taskId}`)

// ---- Wiki ----
export const generateWiki = (id) => request.post(`/knowledge/${id}/wiki/generate`)
export const listWikiPages = (id) => request.get(`/knowledge/${id}/wiki`)
export const getWikiPage = (id, pageId) => request.get(`/knowledge/${id}/wiki/${pageId}`)
export const updateWikiPage = (id, pageId, data) => request.put(`/knowledge/${id}/wiki/${pageId}`, data)
export const deleteWiki = (id) => request.delete(`/knowledge/${id}/wiki`)

// ---- 消息中心（邀请） ----
export const listKnowledgeInvitations = () => request.get('/knowledge/invitations')
export const readKnowledgeInvitation = (id) => request.post(`/knowledge/invitations/${id}/read`)
export const acceptKnowledgeInvitation = (id) => request.post(`/knowledge/invitations/${id}/accept`)
export const rejectKnowledgeInvitation = (id) => request.post(`/knowledge/invitations/${id}/reject`)
