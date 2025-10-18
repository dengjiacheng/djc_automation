import type { RouteRecordRaw } from 'vue-router'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../../modules/auth/views/LoginView.vue'),
    meta: {
      public: true,
    },
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('../../modules/common/components/RoleRedirect.vue'),
    meta: {
      requiresAuth: true,
    },
  },
  {
    path: '/admin',
    component: () => import('../layouts/AdminLayout.vue'),
    meta: {
      requiresAuth: true,
      roles: ['admin', 'super_admin'],
    },
    children: [
      {
        path: '',
        name: 'admin.home',
        component: () => import('../../modules/admin/views/AdminDashboard.vue'),
      },
      {
        path: 'devices',
        name: 'admin.devices',
        component: () => import('../../modules/admin/views/DevicesView.vue'),
      },
      {
        path: 'accounts',
        name: 'admin.accounts',
        component: () => import('../../modules/admin/views/AccountsView.vue'),
      },
    ],
  },
  {
    path: '/customer',
    component: () => import('../layouts/CustomerLayout.vue'),
    meta: {
      requiresAuth: true,
      roles: ['user', 'customer'],
    },
    children: [
      {
        path: '',
        name: 'customer.home',
        component: () => import('../../modules/customer/views/CustomerDashboard.vue'),
      },
      {
        path: 'devices',
        name: 'customer.devices',
        component: () => import('../../modules/customer/views/DevicesView.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../../modules/common/components/NotFoundView.vue'),
    meta: {
      public: true,
    },
  },
]
