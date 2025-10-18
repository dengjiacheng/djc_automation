<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'

import DeviceTable from '../../common/components/DeviceTable.vue'
import { deviceApi } from '../../../shared/api/services/devices'
import type { Device } from '../../../shared/api/types'

const filters = reactive({
  username: '',
  onlineOnly: false,
})

const loading = ref(false)
const devices = ref<Device[]>([])
const total = ref(0)
const errorMessage = ref<string | null>(null)

const fetchDevices = async () => {
  loading.value = true
  errorMessage.value = null
  try {
    const response = await deviceApi.listForAdmin({
      username: filters.username || undefined,
      online_only: filters.onlineOnly,
    })
    devices.value = response.devices
    total.value = response.total
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.detail ?? '获取设备列表失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => [filters.username, filters.onlineOnly],
  () => {
    fetchDevices()
  },
  { flush: 'post' },
)

onMounted(fetchDevices)
</script>

<template>
  <section class="devices">
    <header class="header">
      <div>
        <h2>设备管理</h2>
        <p>查看并管理所有账号下的设备状态</p>
      </div>
      <div class="filters">
        <input v-model="filters.username" type="search" placeholder="按账号筛选" />
        <label>
          <input v-model="filters.onlineOnly" type="checkbox" />
          <span>仅显示在线</span>
        </label>
        <button type="button" @click="fetchDevices" :disabled="loading">手动刷新</button>
      </div>
    </header>

    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <DeviceTable
      :devices="devices"
      :loading="loading"
      show-owner
      @refresh="fetchDevices"
    />
    <footer class="summary">共 {{ total }} 台设备</footer>
  </section>
</template>

<style scoped>
.devices {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 1rem;
}

.header h2 {
  margin: 0;
  font-size: 1.35rem;
}

.filters {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

input[type='search'] {
  border-radius: 0.65rem;
  border: 1px solid rgba(14, 33, 62, 0.1);
  padding: 0.5rem 0.75rem;
}

label {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.9rem;
}

button {
  border: none;
  background: #1c64d1;
  color: #fff;
  border-radius: 0.65rem;
  padding: 0.45rem 0.9rem;
  cursor: pointer;
}

button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.error {
  color: #d64545;
}

.summary {
  font-size: 0.9rem;
  color: #506079;
}

@media (max-width: 768px) {
  .filters {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
}
</style>
