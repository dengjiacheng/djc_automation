import './style.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './app/router'
import { setupRouterGuards } from './app/router/guards'
import { useAuthStore } from './app/store/auth'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)

const authStore = useAuthStore(pinia)
authStore.bootstrap()

setupRouterGuards(router, authStore)

app.use(router)
app.mount('#app')
