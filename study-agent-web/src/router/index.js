import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('../views/LoginView.vue'), meta: { guest: true } },
    { path: '/dashboard', component: () => import('../layouts/BasicLayout.vue'), children: [
      { path: '', component: () => import('../views/DashboardView.vue') }
    ] },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.guest && auth.isAuthenticated) return '/dashboard'
  if (!to.meta.guest && !auth.isAuthenticated) return '/login'
})

export default router
