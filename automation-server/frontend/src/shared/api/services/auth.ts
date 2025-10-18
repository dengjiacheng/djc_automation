import { httpClient } from '../client'
import type { AccountLoginResponse } from '../types'

const mapResponse = (payload: AccountLoginResponse): AccountLoginResponse => ({
  ...payload,
})

export const authApi = {
  async webLogin(username: string, password: string): Promise<AccountLoginResponse> {
    const { data } = await httpClient.post<AccountLoginResponse>('/auth/web/login', {
      username,
      password,
    })
    return mapResponse(data)
  },

  async adminLogin(username: string, password: string): Promise<AccountLoginResponse> {
    const { data } = await httpClient.post<AccountLoginResponse>('/auth/admin/login', {
      username,
      password,
    })
    return mapResponse(data)
  },
}
