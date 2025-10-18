export type AccountRole = 'user' | 'customer' | 'admin' | 'super_admin'

export interface AccountLoginResponse {
  access_token: string
  token_type: string
  account_id: string
  username: string
  role: AccountRole
  is_super_admin?: boolean
}

export interface Device {
  id: string
  username: string
  device_name: string | null
  device_model: string | null
  android_version: string | null
  local_ip: string | null
  public_ip: string | null
  is_online: boolean
  created_at: string
  last_online_at: string | null
}

export interface DeviceListResponse {
  total: number
  devices: Device[]
}

export interface AdminStats {
  device_total: number
  device_online: number
  device_offline: number
  today_commands: number
}

export interface CommandRequestPayload {
  action: string
  params?: Record<string, unknown>
}

export interface ApiResult<T> {
  data: T
}
