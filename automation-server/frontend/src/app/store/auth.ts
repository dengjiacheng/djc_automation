import { computed, ref } from 'vue'
import { defineStore, type Pinia } from 'pinia'

import { authApi } from '../../shared/api/services/auth'
import type { AccountLoginResponse, AccountRole } from '../../shared/api/types'

const STORAGE_KEY = 'aas.auth'

type StoredSession = {
  token: string
  accountId: string
  username: string
  role: AccountRole
}

const persistSession = (session: StoredSession | null) => {
  if (!session) {
    localStorage.removeItem(STORAGE_KEY)
    return
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

const loadSession = (): StoredSession | null => {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredSession
  } catch (error) {
    console.warn('Failed to parse stored session', error)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const accountId = ref<string | null>(null)
  const username = ref<string | null>(null)
  const role = ref<AccountRole | null>(null)
  const initialised = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value && accountId.value))
  const isAdmin = computed(() => role.value === 'admin' || role.value === 'super_admin')

  const setSession = (payload: AccountLoginResponse) => {
    token.value = payload.access_token
    accountId.value = payload.account_id
    username.value = payload.username
    role.value = payload.role
    persistSession({
      token: payload.access_token,
      accountId: payload.account_id,
      username: payload.username,
      role: payload.role,
    })
  }

  const clearSession = () => {
    token.value = null
    accountId.value = null
    username.value = null
    role.value = null
    persistSession(null)
  }

  const bootstrap = async () => {
    if (initialised.value) return
    await restoreSession()
  }

  const restoreSession = async () => {
    const stored = loadSession()
    if (stored) {
      token.value = stored.token
      accountId.value = stored.accountId
      username.value = stored.username
      role.value = stored.role
    }
    initialised.value = true
  }

  const login = async (credentials: { username: string; password: string; admin?: boolean }) => {
    const response = credentials.admin
      ? await authApi.adminLogin(credentials.username, credentials.password)
      : await authApi.webLogin(credentials.username, credentials.password)
    setSession(response)
    return response
  }

  const logout = () => {
    clearSession()
  }

  return {
    token,
    accountId,
    username,
    role,
    initialised,
    isAuthenticated,
    isAdmin,
    bootstrap,
    restoreSession,
    login,
    logout,
    clearSession,
  }
})

export type UseAuthStore = ReturnType<typeof useAuthStore>

export const getAuthStore = (pinia: Pinia) => useAuthStore(pinia)
