<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'

import { useAuthStore } from '../../../app/store/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: '',
  admin: false,
})

const loading = ref(false)
const errorMessage = ref<string | null>(null)

const handleSubmit = async () => {
  if (loading.value) return
  errorMessage.value = null
  loading.value = true
  try {
    await authStore.login(form)
    const target = (route.query.redirect as string | undefined) ?? '/dashboard'
    router.replace(target)
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.detail ?? '登录失败，请检查账号或密码'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="panel">
      <header>
        <h1>Android Automation 控制台</h1>
        <p>使用管理员或普通用户身份登录系统</p>
      </header>
      <form class="form" @submit.prevent="handleSubmit">
        <label class="field">
          <span>用户名</span>
          <input v-model="form.username" type="text" required placeholder="请输入用户名" />
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="form.password" type="password" required placeholder="请输入密码" />
        </label>
        <label class="checkbox">
          <input v-model="form.admin" type="checkbox" />
          <span>使用管理员身份登录</span>
        </label>
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background: radial-gradient(circle at top, #e8f0ff 0%, #fafcff 60%);
}

.panel {
  width: min(480px, 100%);
  padding: 2.5rem;
  border-radius: 1.5rem;
  background: #fff;
  box-shadow: 0 32px 80px rgba(10, 30, 65, 0.12);
  border: 1px solid rgba(17, 42, 85, 0.08);
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

header h1 {
  margin: 0;
  font-size: 1.75rem;
  color: #0d1b2a;
}

header p {
  margin: 0.5rem 0 0;
  color: #4b5a6b;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.95rem;
  color: #233142;
}

input[type='text'],
input[type='password'] {
  border-radius: 0.75rem;
  border: 1px solid rgba(14, 33, 62, 0.12);
  padding: 0.75rem 1rem;
  font-size: 1rem;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

input:focus {
  outline: none;
  border-color: #2b7fff;
  box-shadow: 0 0 0 3px rgba(43, 127, 255, 0.15);
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: #233142;
}

button[type='submit'] {
  margin-top: 0.5rem;
  background: linear-gradient(120deg, #2b7fff, #64a9ff);
  border: none;
  color: #fff;
  padding: 0.75rem;
  border-radius: 0.75rem;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

button[type='submit']:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 12px 20px rgba(43, 127, 255, 0.25);
}

button[type='submit']:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.error {
  color: #d64545;
  font-size: 0.9rem;
}

@media (max-width: 640px) {
  .panel {
    padding: 1.75rem;
  }
}
</style>
