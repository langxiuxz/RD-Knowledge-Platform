import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

const TOKEN_KEY = 'study-agent.access-token'
const REFRESH_TOKEN_KEY = 'study-agent.refresh-token'
const USER_KEY = 'study-agent.user'

function readUser() {
  try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch { return null }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const refreshToken = ref(localStorage.getItem(REFRESH_TOKEN_KEY) || '')
  const user = ref(readUser())
  const isAuthenticated = computed(() => Boolean(token.value))

  function persist() {
    token.value ? localStorage.setItem(TOKEN_KEY, token.value) : localStorage.removeItem(TOKEN_KEY)
    refreshToken.value ? localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken.value) : localStorage.removeItem(REFRESH_TOKEN_KEY)
    user.value ? localStorage.setItem(USER_KEY, JSON.stringify(user.value)) : localStorage.removeItem(USER_KEY)
  }

  async function login(payload) {
    const { data } = await authApi.login(payload)
    token.value = data.data.token
    refreshToken.value = data.data.refreshToken
    user.value = data.data.user
    persist()
    return user.value
  }

  async function register(payload) { return authApi.register(payload) }

  async function loadCurrent() {
    if (!token.value) return null
    const { data } = await authApi.getCurrentUser()
    user.value = data.data
    persist()
    return user.value
  }

  async function refresh() {
    if (!refreshToken.value) throw new Error('No refresh token')
    const { data } = await authApi.refresh(refreshToken.value)
    token.value = data.data.token
    refreshToken.value = data.data.refreshToken
    user.value = data.data.user || user.value
    persist()
  }

  async function updateProfile(payload) {
    await authApi.updateProfile(payload)
    return loadCurrent()
  }

  async function changePassword(payload) { await authApi.changePassword(payload) }

  async function logout() {
    try { if (token.value) await authApi.logout() } finally { clear() }
  }

  function clear() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    persist()
  }

  return { token, refreshToken, user, isAuthenticated, login, register, loadCurrent, refresh, updateProfile, changePassword, logout, clear }
})
