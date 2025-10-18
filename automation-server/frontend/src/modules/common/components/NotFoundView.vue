<template>
  <section class="not-found">
    <h1>页面未找到</h1>
    <p>抱歉，您访问的页面不存在或已被移除。</p>
    <RouterLink to="/dashboard" class="link">返回控制台</RouterLink>
  </section>
</template>

<style scoped>
.not-found {
  display: grid;
  place-items: center;
  gap: 0.75rem;
  min-height: 60vh;
  text-align: center;
}

.not-found h1 {
  font-size: clamp(2rem, 3vw, 2.5rem);
}

.link {
  color: var(--brand-primary, #2b7fff);
  text-decoration: none;
}

.link:hover {
  text-decoration: underline;
}
</style>
