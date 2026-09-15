import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import MainLayout from '../layouts/MainLayout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: MainLayout,
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/DashboardView.vue'),
        meta: { title: '仪表盘' },
      },
      { path: '', redirect: '/dashboard' },
      {
        path: 'chat/:sessionId?',
        name: 'Chat',
        component: () => import('../views/ChatView.vue'),
        meta: { title: '对话' },
      },
      {
        path: 'knowledge',
        name: 'KnowledgeList',
        component: () => import('../views/KnowledgeListView.vue'),
        meta: { title: '我的知识库' },
      },
      {
        path: 'knowledge/:id',
        name: 'KnowledgeDetail',
        component: () => import('../views/KnowledgeDetailView.vue'),
        meta: { title: '知识库详情' },
      },
      {
        path: 'public-knowledge',
        name: 'PublicKnowledge',
        component: () => import('../views/PublicKnowledgeView.vue'),
        meta: { title: '公开知识库' },
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('../views/KnowledgeMessagesView.vue'),
        meta: { title: '消息中心' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { title: '个人资料' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/SettingsView.vue'),
        meta: { title: '设置' },
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('../views/AdminUsersView.vue'),
        meta: { admin: true, title: '用户管理' },
      },
      {
        path: 'admin/knowledge',
        name: 'AdminKnowledge',
        component: () => import('../views/AdminKnowledgeView.vue'),
        meta: { admin: true, title: '全局知识库' },
      },
      {
        path: 'admin/knowledge/:id',
        name: 'AdminKnowledgeDetail',
        component: () => import('../views/AdminKnowledgeDetailView.vue'),
        meta: { admin: true, title: '全局知识库详情' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('../views/NotFoundView.vue'),
        meta: { title: '未找到' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    if (to.path === '/login' && authStore.isLogin) return '/dashboard'
    return true
  }

  if (!authStore.isLogin) {
    ElMessage.warning('请先登录')
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (to.meta.admin && !authStore.isAdmin) {
    ElMessage.error('无权限访问该页面')
    return '/dashboard'
  }

  return true
})

export default router
