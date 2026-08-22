import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
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
      { path: 'chat', name: 'Chat', component: () => import('../views/ChatView.vue') },
      { path: 'knowledge', name: 'KnowledgeList', component: () => import('../views/KnowledgeListView.vue') },
      { path: 'knowledge/:id', name: 'KnowledgeDetail', component: () => import('../views/KnowledgeDetailView.vue') },
      { path: 'admin/users', name: 'AdminUsers', component: () => import('../views/AdminUsersView.vue'), meta: { admin: true } },
      { path: 'admin/knowledge', name: 'AdminKnowledge', component: () => import('../views/AdminKnowledgeView.vue'), meta: { admin: true } },
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
  // 公开页（登录）直接放行
  if (to.meta.public) {
    next()
    return
  }
  // 未登录 → 登录页，并记录来源
  if (!authStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  // 管理员专属页 → 校验角色
  if (to.meta.admin && !authStore.isAdmin) {
    ElMessage.error('无权限访问该页面')
    next('/knowledge')
    return
  }
  next()
})

export default router
