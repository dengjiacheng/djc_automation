import { httpClient } from '../client'
import type { AccountLoginResponse, AccountRole, AdminStats } from '../types'

export interface AccountListItem {
  id: string
  username: string
  role: AccountRole
  email?: string | null
  is_active: boolean
  created_at: string
  last_login_at?: string | null
}

export const adminApi = {
  async getStats(): Promise<AdminStats> {
    const { data } = await httpClient.get<AdminStats>('/admin/stats')
    return data
  },

  async listAccounts(): Promise<AccountListItem[]> {
    const { data } = await httpClient.get<AccountListItem[]>('/admin/accounts')
    return data
  },

  async createAccount(payload: { username: string; password: string; role: string; email?: string | null }) {
    const { data } = await httpClient.post<AccountLoginResponse>('/admin/accounts', payload)
    return data
  },
}
