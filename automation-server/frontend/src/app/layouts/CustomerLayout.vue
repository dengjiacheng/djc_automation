<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'

import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()
const router = useRouter()

const navigation = [
  { label: '我的概览', name: 'customer.home' },
  { label: '我的设备', name: 'customer.devices' },
]

const currentUser = computed(() => ({
  username: authStore.username,
}))

const handleLogout = () => {
  authStore.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <header class="header">
      <div class="brand">
        <span>Automation 客户中心</span>
      </div>
      <nav class="nav">
        <RouterLink
          v-for="item in navigation"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav-link"
          active-class="active"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
      <div class="actions">
        <span class="username">{{ currentUser.username }}</span>
        <button type="button" class="logout" @click="handleLogout">退出</button>
      </div>
    </header>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  color: #1d2430;
}

.header {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 2rem;
  border-bottom: 1px solid rgba(13, 27, 42, 0.08);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  position: sticky;
  top: 0;
  z-index: 5;
}

.brand span {
  font-size: 1.1rem;
  font-weight: 600;
}

.nav {
  display: flex;
  gap: 0.5rem;
}

.nav-link {
  padding: 0.4rem 0.8rem;
  border-radius: 999px;
  text-decoration: none;
  color: inherit;
  transition: background-color 0.2s ease;
}

.nav-link:hover,
.nav-link.active {
  background-color: rgba(43, 127, 255, 0.12);
}

.actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.9rem;
}

.logout {
  border: none;
  background: #2b7fff;
  color: #fff;
  border-radius: 999px;
  padding: 0.4rem 0.9rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.logout:hover {
  background-color: #1c64d1;
}

.content {
  padding: 2rem;
  max-width: 1100px;
  margin: 0 auto;
}

@media (max-width: 768px) {
  .header {
    flex-direction: column;
    align-items: flex-start;
  }

  .nav {
    width: 100%;
  }

  .actions {
    align-self: stretch;
    justify-content: space-between;
  }
}
</style>
