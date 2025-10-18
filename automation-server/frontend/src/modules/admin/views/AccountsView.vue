<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'

import { adminApi } from '../../../shared/api/services/admin'
import type { AccountListItem } from '../../../shared/api/services/admin'
import type { AccountRole } from '../../../shared/api/types'

const accounts = ref<AccountListItem[]>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const formVisible = ref(false)

const form = reactive({
  username: '',
  password: '',
  role: 'user' as AccountRole,
  email: '',
})

const fetchAccounts = async () => {
  loading.value = true
  errorMessage.value = null
  try {
    accounts.value = await adminApi.listAccounts()
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.detail ?? '获取账号列表失败'
  } finally {
    loading.value = false
  }
}

const handleCreate = async () => {
  if (!form.username || !form.password) return
  loading.value = true
  errorMessage.value = null
  try {
    await adminApi.createAccount({
      username: form.username,
      password: form.password,
      role: form.role,
      email: form.email || undefined,
    })
    formVisible.value = false
    form.username = ''
    form.password = ''
    form.role = 'user'
    form.email = ''
    await fetchAccounts()
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.detail ?? '创建账号失败'
  } finally {
    loading.value = false
  }
}

onMounted(fetchAccounts)
</script>

<template>
  <section class="accounts">
    <header class="header">
      <div>
        <h2>账号管理</h2>
        <p>为客户或管理员创建账号，管理权限与状态</p>
      </div>
      <button type="button" @click="formVisible = !formVisible">
        {{ formVisible ? '收起表单' : '新建账号' }}
      </button>
    </header>

    <form v-if="formVisible" class="form" @submit.prevent="handleCreate">
      <label>
        <span>用户名</span>
        <input v-model="form.username" type="text" required placeholder="请输入用户名" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="form.password" type="password" required placeholder="建议使用强密码" />
      </label>
      <label>
        <span>邮箱</span>
        <input v-model="form.email" type="email" placeholder="选填" />
      </label>
      <label>
        <span>角色</span>
        <select v-model="form.role">
          <option value="user">普通用户</option>
          <option value="customer">客户</option>
          <option value="admin">管理员</option>
          <option value="super_admin">超级管理员</option>
        </select>
      </label>
      <div class="form-actions">
        <button type="submit" :disabled="loading">{{ loading ? '创建中...' : '创建账号' }}</button>
      </div>
    </form>

    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <div class="table-wrapper">
      <table>
        <thead>
          <tr>
            <th>用户名</th>
            <th>角色</th>
            <th>邮箱</th>
            <th>状态</th>
            <th>最近登录</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="5" class="placeholder">加载中...</td>
          </tr>
          <tr v-else-if="!accounts.length">
            <td colspan="5" class="placeholder">暂无账号</td>
          </tr>
          <tr v-for="account in accounts" :key="account.id">
            <td>
              <strong>{{ account.username }}</strong>
              <small>{{ account.id }}</small>
            </td>
            <td>{{ account.role }}</td>
            <td>{{ account.email ?? '—' }}</td>
            <td>
              <span :class="['badge', account.is_active ? 'active' : 'inactive']">
                {{ account.is_active ? '启用' : '停用' }}
              </span>
            </td>
            <td>{{ account.last_login_at ?? '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.accounts {
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

button {
  border: none;
  background: #0f62fe;
  color: #fff;
  border-radius: 0.6rem;
  padding: 0.45rem 0.9rem;
  cursor: pointer;
}

.form {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  background: #fff;
  padding: 1.25rem;
  border-radius: 1rem;
  border: 1px solid rgba(17, 42, 85, 0.08);
  box-shadow: 0 16px 30px rgba(22, 44, 76, 0.08);
}

label {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.9rem;
}

input,
select {
  border-radius: 0.6rem;
  border: 1px solid rgba(14, 33, 62, 0.1);
  padding: 0.55rem 0.75rem;
  font-size: 0.95rem;
}

.form-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
}

.table-wrapper {
  overflow-x: auto;
  background: #fff;
  border-radius: 1rem;
  border: 1px solid rgba(17, 42, 85, 0.08);
  box-shadow: 0 16px 30px rgba(22, 44, 76, 0.08);
}

table {
  width: 100%;
  border-collapse: collapse;
  min-width: 640px;
}

th,
td {
  padding: 0.85rem 1rem;
  text-align: left;
}

th {
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #5d6a7d;
  border-bottom: 1px solid rgba(17, 42, 85, 0.08);
}

td {
  border-top: 1px solid rgba(17, 42, 85, 0.05);
}

.placeholder {
  text-align: center;
  color: #5d6a7d;
}

td strong {
  display: block;
  font-weight: 600;
}

td small {
  display: block;
  color: #8b97ad;
  font-size: 0.75rem;
}

.badge {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.badge.active {
  background: rgba(34, 197, 94, 0.12);
  color: #1b9d56;
}

.badge.inactive {
  background: rgba(248, 113, 113, 0.12);
  color: #d64545;
}

.error {
  color: #d64545;
}
</style>
