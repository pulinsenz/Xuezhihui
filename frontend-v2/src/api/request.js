import axios from 'axios'
import { toast } from '../components/toast'

/**
 * axios 封装（约定同老前端）：
 * - 自动携带 Authorization: Bearer {token}
 * - code===0 返回 data；40100 清登录态并派发 auth:expired（router 监听跳登录页）
 * - 其余错误统一 toast
 */

// 登录失效提醒只弹一次（并发 40100 去重），重新登录后复位
let authExpiredNotified = false

export function resetAuthExpiredNotice() {
  authExpiredNotified = false
}

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res instanceof Blob) return res
    if (res.code === 0) return res.data
    if (res.code === 40100) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!authExpiredNotified) {
        authExpiredNotified = true
        toast.error(res.message || '请重新登录')
        window.dispatchEvent(new Event('auth:expired'))
      }
    } else {
      toast.error(res.message || '请求失败')
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    toast.error(error.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
