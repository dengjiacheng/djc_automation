<script setup lang="ts">
import { onMounted, ref } from 'vue'

import DeviceTable from '../../common/components/DeviceTable.vue'
import { deviceApi } from '../../../shared/api/services/devices'
import type { Device } from '../../../shared/api/types'

const devices = ref<Device[]>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)

const fetchDevices = async () => {
  loading.value = true
  errorMessage.value = null
  try {
    const response = await deviceApi.listForCustomer()
    devices.value = response.devices
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.detail ?? '获取设备列表失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchDevices)
</script>

<template>
  <section class="devices">
    <header>
      <h2>我的设备</h2>
      <p>查看设备连接状态与网络信息，确保设备在线与运维正常。</p>
      <button type="button" @click="fetchDevices" :disabled="loading">
        {{ loading ? '刷新中...' : '刷新列表' }}
      </button>
    </header>

    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <DeviceTable :devices="devices" :loading="loading" @refresh="fetchDevices" />
  </section>
</template>

<style scoped>
.devices {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}

header h2 {
  margin: 0;
  font-size: 1.4rem;
}

header p {
  margin: 0;
  color: #4b586c;
  flex: 1;
  min-width: 240px;
}

button {
  border: none;
  background: #2b7fff;
  color: #fff;
  border-radius: 0.6rem;
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
</style>
