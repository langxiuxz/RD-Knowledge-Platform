import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

let refreshPromise = null

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
    if (!refreshPromise) {
      refreshPromise = authStore.refresh().finally(() => { refreshPromise = null })
    }
    try {
      await refreshPromise
      original.headers.Authorization = `Bearer ${authStore.token}`
      return request(original)
    } catch (refreshError) {
      authStore.clear()
      return Promise.reject(refreshError)
    }
  }
)

export default request
