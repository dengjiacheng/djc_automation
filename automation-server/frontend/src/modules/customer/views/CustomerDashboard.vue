<script setup lang="ts">
import { computed } from 'vue'

import { useAuthStore } from '../../../app/store/auth'

const authStore = useAuthStore()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
</script>

<template>
  <section class="dashboard">
    <header>
      <h2>{{ greeting }}，{{ authStore.username }}</h2>
      <p>欢迎回到设备控制面板。您可以在左侧导航中查看并管理设备。</p>
    </header>
    <div class="tips">
      <article>
        <h3>快速开始</h3>
        <ol>
          <li>在「我的设备」中查看设备在线状态和连接信息。</li>
          <li>使用移动端或 Web UI 连接后，可实时接收指令与日志。</li>
          <li>遇到问题时，请联系管理员协助排查。</li>
        </ol>
      </article>
      <article>
        <h3>安全建议</h3>
        <ul>
          <li>定期更换账号密码，避免账号共享。</li>
          <li>离开设备前，请确保退出控制会话。</li>
          <li>如发现异常登录，请立即通知管理员。</li>
        </ul>
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

header h2 {
  margin: 0;
  font-size: 1.5rem;
}

header p {
  margin: 0.5rem 0 0;
  color: #546073;
}

.tips {
  display: grid;
  gap: 1rem;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}

article {
  background: #fff;
  border-radius: 1rem;
  padding: 1.5rem;
  box-shadow: 0 16px 30px rgba(17, 37, 73, 0.07);
  border: 1px solid rgba(15, 35, 65, 0.08);
}

article h3 {
  margin: 0 0 0.75rem;
  font-size: 1.1rem;
}

ul,
ol {
  margin: 0;
  padding-left: 1.25rem;
  color: #444;
  display: grid;
  gap: 0.4rem;
}
</style>
