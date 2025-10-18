import axios from 'axios'

import { getEnv } from '../config/env'
import { useAuthStore } from '../../app/store/auth'

const env = getEnv()

export const httpClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 10000,
})

httpClient.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      const authStore = useAuthStore()
      authStore.clearSession()
    }
    return Promise.reject(error)
  },
)
