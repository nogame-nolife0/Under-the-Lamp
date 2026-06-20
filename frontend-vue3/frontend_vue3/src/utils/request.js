import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 1200000,
})

function handleUnauthorized(message) {
  localStorage.removeItem('pg_token')
  const path = window.location.pathname
  if (!path.startsWith('/login') && !path.startsWith('/register')) {
    ElMessage.error(message || '未登录或登录已过期，请重新登录')
    window.location.href = '/login'
  }
}

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('pg_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const result = response.data
    if (result && typeof result.code === 'number') {
      if (result.code === 200) {
        return result.data
      }
      if (result.code === 401) {
        handleUnauthorized(result.msg)
        return Promise.reject(new Error(result.msg || '未登录或登录已过期'))
      }
      ElMessage.error(result.msg || '请求失败')
      return Promise.reject(new Error(result.msg || '请求失败'))
    }
    return response.data
  },
  (error) => {
    const status = error.response?.status
    const msg = error.response?.data?.msg || error.message || '网络异常'
    if (status === 401) {
      handleUnauthorized(msg)
    } else {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
