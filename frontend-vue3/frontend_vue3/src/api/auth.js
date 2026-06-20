import request from '@/utils/request'

export function getAuthStatus() {
  return request.get('/auth/status')
}

export function register(data) {
  return request.post('/auth/register', data)
}

export function login(data) {
  return request.post('/auth/login', data)
}

export function forgotPassword(phone) {
  return request.post('/auth/forgot-password', { phone })
}

export function getProfile() {
  return request.get('/auth/me')
}

export function logout() {
  return request.post('/auth/logout')
}
