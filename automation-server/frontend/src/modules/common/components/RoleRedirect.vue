<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '../../../app/store/auth'

const router = useRouter()
const authStore = useAuthStore()

onMounted(() => {
  if (!authStore.isAuthenticated) {
    router.replace({ name: 'login' })
    return
  }

  if (authStore.role === 'admin' || authStore.role === 'super_admin') {
    router.replace({ name: 'admin.home' })
    return
  }

  router.replace({ name: 'customer.home' })
})
</script>

<template>
  <div class="redirect-view">
    <span>正在跳转...</span>
  </div>
</template>

<style scoped>
.redirect-view {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  font-size: 1rem;
  color: var(--text-muted);
}
</style>
