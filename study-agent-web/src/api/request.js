import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

let refreshPromise = null

// REST requests and SSE requests share the same refresh operation. This keeps
// refresh-token rotation from racing when several requests receive 401 at once.
export function refreshAccessToken() {
  const authStore = useAuthStore()
  if (!refreshPromise) {
    refreshPromise = authStore.refresh().finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

function clearAuthenticationAndRedirect(authStore) {
  authStore.clear()
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

request.interceptors.request.use((config) => {
  const token = useAuthStore().token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (response) => response,
  async (error) => {
    const authStore = useAuthStore()
    const original = error.config
    if (error.response?.status !== 401 || original?._retry || original?.url?.includes('/auth/refresh')) {
      return Promise.reject(error)
    }
    original._retry = true
    try {
      await refreshAccessToken()
      original.headers.Authorization = `Bearer ${authStore.token}`
      return request(original)
    } catch (refreshError) {
      clearAuthenticationAndRedirect(authStore)
      return Promise.reject(refreshError)
    }
  }
)

export default request
