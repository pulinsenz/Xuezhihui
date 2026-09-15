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
        meta: { public: true, title: '仪表盘' },
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
        meta: { loginRequired: true, title: '我的知识库' },
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
        meta: { loginRequired: true, title: '公开知识库' },
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('../views/KnowledgeMessagesView.vue'),
        meta: { loginRequired: true, title: '消息中心' },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { loginRequired: true, title: '个人资料' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/SettingsView.vue'),
        meta: { loginRequired: true, title: '设置' },
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('../views/AdminUsersView.vue'),
        meta: { admin: true, loginRequired: true, title: '用户管理' },
      },
      {
        path: 'admin/knowledge',
        name: 'AdminKnowledge',
        component: () => import('../views/AdminKnowledgeView.vue'),
        meta: { admin: true, loginRequired: true, title: '全局知识库' },
      },
      {
        path: 'admin/knowledge/:id',
        name: 'AdminKnowledgeDetail',
        component: () => import('../views/AdminKnowledgeDetailView.vue'),
        meta: { admin: true, loginRequired: true, title: '全局知识库详情' },
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

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  const uiStore = useUiStore()

  if (to.meta.public) {
    next()
    return
  }

  if (to.meta.loginRequired && !authStore.isLogin) {
    uiStore.openLogin()
    ElMessage.warning('请先登录')
    next('/dashboard')
    return
  }

  if (to.meta.admin) {
    if (!authStore.isLogin) {
      uiStore.openLogin()
      ElMessage.warning('请先登录')
      next('/dashboard')
      return
    }
    if (!authStore.isAdmin) {
      ElMessage.error('无权限访问该页面')
      next('/dashboard')
      return
    }
  }

  next()
})

export default router
