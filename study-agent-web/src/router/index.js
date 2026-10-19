import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/login', component: () => import('../views/LoginView.vue'), meta: { guest: true } },
  { path: '/register', component: () => import('../views/RegisterView.vue'), meta: { guest: true } },
  { path: '/', component: () => import('../layouts/BasicLayout.vue'), children: [
    { path: 'dashboard', component: () => import('../views/DashboardView.vue') },
    { path: 'knowledge-bases', component: () => import('../views/KnowledgeBaseView.vue') },
    { path: 'chat', component: () => import('../views/ChatView.vue') },
    { path: 'quizzes', component: () => import('../views/QuizView.vue') },
    { path: 'wrong-questions', component: () => import('../views/WrongQuestionsView.vue') },
    { path: 'pomodoro', component: () => import('../views/PomodoroView.vue') },
    { path: 'learning', component: () => import('../views/LearningView.vue') },
    { path: 'study-plans', component: () => import('../views/StudyPlanView.vue') },
    { path: 'profile', component: () => import('../views/ProfileView.vue') }
  ]},
  { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') }
]
const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.guest && auth.isAuthenticated) return '/dashboard'
  if (!to.meta.guest && !auth.isAuthenticated) return '/login'
})
export default router
