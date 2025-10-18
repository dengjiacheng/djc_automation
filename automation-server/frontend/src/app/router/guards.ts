import type { Router } from 'vue-router'

import type { UseAuthStore } from '../store/auth'

export const setupRouterGuards = (router: Router, authStore: UseAuthStore) => {
  router.beforeEach(async (to, _from, next) => {
    const requiresAuth = to.meta?.requiresAuth === true
    const isPublic = to.meta?.public === true

    if (!authStore.initialised) {
      await authStore.restoreSession()
    }

    if (!requiresAuth && isPublic) {
      return next()
    }

    if (requiresAuth && !authStore.isAuthenticated) {
      return next({ name: 'login', query: { redirect: to.fullPath } })
    }

    if (to.meta?.roles && Array.isArray(to.meta.roles)) {
      const allowedRoles = to.meta.roles as string[]
      if (!authStore.role || !allowedRoles.includes(authStore.role)) {
        return next({ name: 'dashboard' })
      }
    }

    return next()
  })
}
