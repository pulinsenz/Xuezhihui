import request from './request'

export const login = (data) => request.post('/auth/login', data)
export const getCaptcha = () => request.get('/auth/captcha')
export const register = (data) => request.post('/auth/register', data)
export const logout = () => request.post('/auth/logout')
export const getCurrentUser = () => request.get('/auth/me')
export const updateProfile = (data) => request.put('/auth/profile', data)
export const changePassword = (data) => request.put('/auth/password', data)

export const uploadAvatar = (file) => {
  const fd = new FormData()
  fd.append('file', file)
  return request.post('/auth/avatar', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
}
