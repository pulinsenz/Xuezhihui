import request from './request'

/** 登录 */
export const login = (data) => request.post('/auth/login', data)

/** 注册 */
export const register = (data) => request.post('/auth/register', data)

/** 登出 */
export const logout = () => request.post('/auth/logout')

/** 获取当前登录用户 */
export const getCurrentUser = () => request.get('/auth/me')
