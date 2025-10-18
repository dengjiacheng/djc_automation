<script setup lang="ts">
import type { Device } from '../../../shared/api/types'

defineProps<{
  devices: Device[]
  loading?: boolean
  showOwner?: boolean
}>()

const emit = defineEmits<{
  refresh: []
}>()

const formatDate = (value: string | null) => {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <div class="table-container">
    <table>
      <thead>
        <tr>
          <th>设备名称</th>
          <th>型号</th>
          <th>Android 版本</th>
          <th v-if="showOwner">账号</th>
          <th>内网 IP</th>
          <th>公网 IP</th>
          <th>状态</th>
          <th>最近在线</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="loading">
          <td colspan="8" class="placeholder">加载中...</td>
        </tr>
        <tr v-else-if="!devices.length">
          <td colspan="8" class="placeholder">
            暂无设备信息。
            <button type="button" class="link" @click="emit('refresh')">刷新</button>
          </td>
        </tr>
        <tr v-for="device in devices" :key="device.id">
          <td>
            <span class="name">{{ device.device_name ?? '未命名设备' }}</span>
            <small class="id">{{ device.id }}</small>
          </td>
          <td>{{ device.device_model ?? '—' }}</td>
          <td>{{ device.android_version ?? '—' }}</td>
          <td v-if="showOwner">{{ device.username }}</td>
          <td>{{ device.local_ip ?? '—' }}</td>
          <td>{{ device.public_ip ?? '—' }}</td>
          <td>
            <span :class="['status', device.is_online ? 'online' : 'offline']">
              {{ device.is_online ? '在线' : '离线' }}
            </span>
          </td>
          <td>{{ formatDate(device.last_online_at) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-container {
  background: #fff;
  border-radius: 1rem;
  border: 1px solid rgba(17, 42, 85, 0.08);
  box-shadow: 0 10px 30px rgba(22, 44, 76, 0.08);
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 720px;
}

thead {
  background: rgba(15, 35, 65, 0.035);
}

th,
td {
  padding: 0.9rem 1rem;
  text-align: left;
  font-size: 0.95rem;
}

th {
  font-weight: 600;
  color: #1b2432;
  border-bottom: 1px solid rgba(17, 42, 85, 0.08);
}

tbody tr + tr td {
  border-top: 1px solid rgba(17, 42, 85, 0.05);
}

.placeholder {
  text-align: center;
  color: #5d6a7d;
}

.link {
  margin-left: 0.25rem;
  background: none;
  border: none;
  color: #2b7fff;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}

.name {
  display: block;
  font-weight: 600;
}

.id {
  display: block;
  color: #718093;
  font-size: 0.78rem;
}

.status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  padding: 0.25rem 0.5rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
}

.status.online {
  background: rgba(46, 204, 113, 0.12);
  color: #1b9d56;
}

.status.offline {
  background: rgba(255, 89, 94, 0.12);
  color: #d64545;
}
</style>
