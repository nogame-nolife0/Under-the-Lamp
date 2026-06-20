import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getAuthStatus, getProfile, login as loginApi, logout as logoutApi, register as registerApi } from '@/api/auth'

const TOKEN_KEY = 'pg_token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const username = ref('')
  const phone = ref('')
  const registered = ref(false)
  const initialized = ref(false)

  function setSession(data) {
    token.value = data.token
    username.value = data.username || ''
    phone.value = data.phone || ''
    localStorage.setItem(TOKEN_KEY, data.token)
  }

  function clearSession() {
    token.value = ''
    username.value = ''
    phone.value = ''
    localStorage.removeItem(TOKEN_KEY)
  }

  async function init() {
    try {
      const status = await getAuthStatus()
      registered.value = !!status.registered
      if (token.value) {
        try {
          const profile = await getProfile()
          username.value = profile.username
          phone.value = profile.phone
        } catch {
          clearSession()
        }
      }
    } finally {
      initialized.value = true
    }
  }

  async function register(form) {
    const data = await registerApi(form)
    setSession(data)
    registered.value = true
    return data
  }

  async function login(form) {
    const data = await loginApi(form)
    setSession(data)
    return data
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      clearSession()
    }
  }

  return {
    token,
    username,
    phone,
    registered,
    initialized,
    init,
    register,
    login,
    logout,
    clearSession,
  }
})
