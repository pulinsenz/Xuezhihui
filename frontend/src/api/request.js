import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * axios 实例封装：
 * - 请求自动携带 Authorization: Bearer {token}
 * - 响应 code!==0 统一报错；40100 清除登录态并派发 auth:expired 事件
 *   （main.js 监听事件跳登录页，解耦请求层与路由层，避免循环依赖）
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 二进制（文件下载）直接返回
    if (res instanceof Blob) {
      return res
    }
    if (res.code === 0) {
      return res.data
    }
    // 登录失效：清状态 + 派发事件
    if (res.code === 40100) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      ElMessage.error(res.message || '请重新登录')
      window.dispatchEvent(new Event('auth:expired'))
    } else {
      ElMessage.error(res.message || '请求失败')
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
