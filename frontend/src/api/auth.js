import request from './request'

/** 登录 */
export const login = (data) => request.post('/auth/login', data)

/** 获取注册算术验证码（自研，返回 challengeId + 图片 base64） */
export const getCaptcha = () => request.get('/auth/captcha')

/** 注册 */
export const register = (data) => request.post('/auth/register', data)

/** 登出 */
export const logout = () => request.post('/auth/logout')

/** 获取当前登录用户 */
export const getCurrentUser = () => request.get('/auth/me')

/** 更新个人资料（昵称/头像/简介） */
export const updateProfile = (data) => request.put('/auth/profile', data)

/** 修改密码 */
export const changePassword = (data) => request.put('/auth/password', data)

/** 上传头像，返回可公网访问的 URL */
export const uploadAvatar = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/auth/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
