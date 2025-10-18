<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { adminApi } from '../../../shared/api/services/admin'
import type { AdminStats } from '../../../shared/api/types'

const stats = ref<AdminStats | null>(null)
const loading = ref(false)
const errorMessage = ref<string | null>(null)

const fetchStats = async () => {
  loading.value = true
  errorMessage.value = null
  try {
    stats.value = await adminApi.getStats()
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.detail ?? '无法获取统计信息'
  } finally {
    loading.value = false
  }
}

onMounted(fetchStats)
</script>

<template>
  <section class="dashboard">
    <header class="header">
      <div>
        <h2>运营概览</h2>
        <p>掌握当前在线设备、任务下发以及账号使用情况</p>
      </div>
      <button type="button" @click="fetchStats" :disabled="loading">
        {{ loading ? '刷新中...' : '刷新统计' }}
      </button>
    </header>

    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <div v-if="stats" class="grid">
      <article class="card">
        <h3>设备总数</h3>
        <strong>{{ stats.device_total }}</strong>
        <p>目前系统已注册的全部设备数量。</p>
      </article>

      <article class="card">
        <h3>在线设备</h3>
        <strong>{{ stats.device_online }}</strong>
        <p>当前保持连接的在线设备。</p>
      </article>

      <article class="card">
        <h3>离线设备</h3>
        <strong>{{ stats.device_offline }}</strong>
        <p>当前离线的设备数量。</p>
      </article>

      <article class="card">
        <h3>今日指令数</h3>
        <strong>{{ stats.today_commands }}</strong>
        <p>今日累计下发的自动化指令总数。</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.header h2 {
  margin: 0;
  font-size: 1.5rem;
}

.header p {
  margin: 0.35rem 0 0;
  color: #52607a;
}

button {
  border: none;
  background: #2b7fff;
  color: #fff;
  border-radius: 0.65rem;
  padding: 0.5rem 1rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.grid {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}

.card {
  background: #fff;
  border-radius: 1rem;
  padding: 1.25rem;
  box-shadow: 0 16px 30px rgba(22, 44, 76, 0.08);
  border: 1px solid rgba(17, 42, 85, 0.06);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.card h3 {
  margin: 0;
  font-size: 1rem;
  color: #1b2432;
}

.card strong {
  font-size: 2rem;
  font-weight: 600;
  color: #0d1b2a;
}

.card p {
  margin: 0;
  color: #5d6a7d;
  font-size: 0.9rem;
}

.error {
  color: #d64545;
}
</style>
