import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * 路由：登录/注册为独立布局（经典风格，不套主布局）；其余页面在 MainLayout 内。
 * 本轮落地：知识库列表 / AI 问答 / 设置；知识库详情、Wiki、公开库、消息中心、
 * 用户管理、回收站、个人资料为下一迭代（导航暂不注册死链接）。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('../layouts/MainLayout.vue'),
      children: [
        { path: '', redirect: '/knowledge' },
        { path: 'knowledge', name: 'knowledge', component: () => import('../views/KnowledgeListView.vue') },
        { path: 'chat', name: 'chat', component: () => import('../views/ChatView.vue') },
        { path: 'settings', name: 'settings', component: () => import('../views/SettingsView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/knowledge' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLogin) {
    return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (to.name === 'login' && auth.isLogin) {
    return { path: '/knowledge' }
  }
})

export default router
