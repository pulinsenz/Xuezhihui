import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import MainLayout from '../layouts/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: MainLayout,
    children: [
      { path: '', redirect: '/chat' },
      { path: 'chat/:sessionId?', name: 'Chat', component: () => import('../views/ChatView.vue') },
      { path: 'knowledge', name: 'KnowledgeList', component: () => import('../views/KnowledgeListView.vue') },
      { path: 'knowledge/:id', name: 'KnowledgeDetail', component: () => import('../views/KnowledgeDetailView.vue') },
      { path: 'public-knowledge', name: 'PublicKnowledge', component: () => import('../views/PublicKnowledgeView.vue') },
      { path: 'messages', name: 'Messages', component: () => import('../views/KnowledgeMessagesView.vue'), meta: { loginRequired: true } },
      { path: 'profile', name: 'Profile', component: () => import('../views/ProfileView.vue') },
      { path: 'settings', name: 'Settings', component: () => import('../views/SettingsView.vue') },
      { path: 'admin/users', name: 'AdminUsers', component: () => import('../views/AdminUsersView.vue'), meta: { admin: true } },
      { path: 'admin/knowledge', name: 'AdminKnowledge', component: () => import('../views/AdminKnowledgeView.vue'), meta: { admin: true } },
      { path: 'admin/knowledge/:id', name: 'AdminKnowledgeDetail', component: () => import('../views/AdminKnowledgeDetailView.vue'), meta: { admin: true } },
      { path: ':pathMatch(.*)*', name: 'NotFound', component: () => import('../views/NotFoundView.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const uiStore = useUiStore()
  // 公开页（登录页）直接放行
  if (to.meta.public) {
    next()
    return
  }
  if (to.meta.loginRequired && !authStore.isLogin) {
    uiStore.openLogin()
    ElMessage.warning('请先登录')
    next('/chat')
    return
  }
  // 默认聊天界面：未登录也可浏览，登录通过右上角弹窗完成
  // 管理员专属页 → 未登录或非管理员均拦截
  if (to.meta.admin) {
    if (!authStore.isLogin) {
      ElMessage.warning('请先登录')
      next('/chat')
      return
    }
    if (!authStore.isAdmin) {
      ElMessage.error('无权限访问该页面')
      next('/knowledge')
      return
    }
  }
  next()
})

export default router
