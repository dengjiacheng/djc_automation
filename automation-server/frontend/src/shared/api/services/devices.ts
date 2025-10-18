import { httpClient } from '../client'
import type { DeviceListResponse } from '../types'

export const deviceApi = {
  async listForAdmin(params: { skip?: number; limit?: number; online_only?: boolean; username?: string } = {}) {
    const { skip = 0, limit = 100, online_only = false, username } = params
    const response = await httpClient.get<DeviceListResponse>('/admin/devices', {
      params: {
        skip,
        limit,
        online_only,
        username,
      },
    })
    return response.data
  },

  async listForCustomer() {
    const response = await httpClient.get<DeviceListResponse>('/customer/devices')
    return response.data
  },
}
