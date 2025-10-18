<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'

import { useAuthStore } from '../store/auth'

const authStore = useAuthStore()
const router = useRouter()

const navigation = [
  { label: '概览', name: 'admin.home' },
  { label: '设备管理', name: 'admin.devices' },
  { label: '账号管理', name: 'admin.accounts' },
]

const currentUser = computed(() => ({
  username: authStore.username,
  role: authStore.role,
}))

const handleLogout = () => {
  authStore.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <h1 class="brand">Automation Admin</h1>
      <nav>
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
      <footer class="sidebar-footer">
        <div class="profile">
          <span class="username">{{ currentUser.username }}</span>
          <span class="role">{{ currentUser.role }}</span>
        </div>
        <button type="button" class="logout" @click="handleLogout">退出登录</button>
      </footer>
    </aside>
    <main class="content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 240px 1fr;
  min-height: 100vh;
  background: var(--surface-0, #f6f8fb);
}

.sidebar {
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
  background: linear-gradient(180deg, #0d1b2a 0%, #172a42 100%);
  color: #fff;
  gap: 1.25rem;
}

.brand {
  font-size: 1.25rem;
  font-weight: 600;
}

nav {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.nav-link {
  color: rgba(255, 255, 255, 0.75);
  text-decoration: none;
  padding: 0.5rem 0.75rem;
  border-radius: 0.5rem;
  transition: background-color 0.2s ease;
}

.nav-link:hover,
.nav-link.active {
  color: #fff;
  background-color: rgba(255, 255, 255, 0.12);
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.profile {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  font-size: 0.875rem;
}

.logout {
  border: 1px solid rgba(255, 255, 255, 0.4);
  background: transparent;
  color: inherit;
  padding: 0.4rem 0.75rem;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.logout:hover {
  background-color: rgba(255, 255, 255, 0.2);
}

.content {
  padding: 2rem;
}

@media (max-width: 1024px) {
  .layout {
    grid-template-columns: 220px 1fr;
  }
}

@media (max-width: 768px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: sticky;
    top: 0;
    z-index: 5;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }

  nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 0.25rem;
  }

  .sidebar-footer {
    align-items: flex-end;
  }
}
</style>
