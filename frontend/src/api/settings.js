import request from './request'

/** 当前用户设置 */
export const getSettings = () => request.get('/settings')

/** 更新"上传文档是否默认入库"：defaultVectorize = 1 | 0 */
export const updateVectorizeDefault = (defaultVectorize) => request.put('/settings/vectorize-default', { defaultVectorize })
