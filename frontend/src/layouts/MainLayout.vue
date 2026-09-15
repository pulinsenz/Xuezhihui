<template>
  <el-container class="app-shell">
    <el-aside class="sidebar" :width="collapsed ? '80px' : '260px'">
      <div class="sidebar-brand">
        <img class="brand-logo" :src="logo" alt="学智汇" />
        <div v-if="!collapsed" class="brand-copy">
          <div class="brand-name">学智汇</div>
          <div class="brand-sub">AI RAG 工作台</div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        class="side-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/dashboard">
          <el-icon><House /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon>
          <span>AI 对话</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Folder /></el-icon>
          <span>我的知识库</span>
        </el-menu-item>
        <el-menu-item index="/public-knowledge">
          <el-icon><Compass /></el-icon>
          <span>公开知识库</span>
        </el-menu-item>
        <el-menu-item index="/messages">
          <el-badge
            :value="messageStore.unreadCount"
            :hidden="messageStore.unreadCount === 0"
            class="menu-badge"
          >
            <el-icon><Bell /></el-icon>
          </el-badge>
          <span>消息通知</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
        <template v-if="authStore.isAdmin">
          <div v-if="!collapsed" class="menu-divider-label">平台管理</div>
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/knowledge">
            <el-icon><Collection /></el-icon>
            <span>全局知识库</span>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="sidebar-footer">
        <el-tooltip :content="collapsed ? '展开侧栏' : '收起侧栏'" placement="right">
          <el-button
            class="collapse-btn"
            circle
            :icon="collapsed ? Expand : Fold"
            @click="toggleSidebar"
          />
        </el-tooltip>
      </div>
    </el-aside>

    <el-container class="content-shell">
      <el-header class="topbar">
        <div class="title-block">
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageSubtitle }}</p>
        </div>

        <div class="topbar-tools">
          <el-badge :value="messageStore.unreadCount" :hidden="messageStore.unreadCount === 0">
            <el-button class="icon-button" circle :icon="Bell" @click="goMessages" />
          </el-badge>
          <el-tag effect="plain" type="info" class="locale-chip">CN ZH</el-tag>

          <template v-if="authStore.isLogin">
            <el-dropdown @command="handleCommand">
              <span class="user-chip">
                <el-avatar :size="34" class="avatar" :src="authStore.user?.userAvatar || undefined">
                  {{ avatarText }}
                </el-avatar>
                <span class="user-copy">
                  <strong>{{ authStore.user?.userName || authStore.user?.userAccount }}</strong>
                  <small>{{ authStore.isAdmin ? '管理员' : '成员' }}</small>
                </span>
                <el-icon class="chevron"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu class="user-dropdown-menu">
                  <el-dropdown-item command="profile">
                    <el-icon><User /></el-icon>
                    <span>个人资料</span>
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">
                    <el-icon><SwitchButton /></el-icon>
                    <span>退出登录</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>

          <template v-else>
            <el-button type="primary" :icon="User" @click="uiStore.openLogin()">登录</el-button>
          </template>
        </div>
      </el-header>

      <el-main class="page-body">
        <router-view />
      </el-main>
    </el-container>

    <LoginDialog v-model="uiStore.loginDialogVisible" />
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Bell,
  ChatDotRound,
  Collection,
  Compass,
  Expand,
  Fold,
  Folder,
  House,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useMessageStore } from '../stores/messages'
import { useUiStore } from '../stores/ui'
import LoginDialog from '../components/LoginDialog.vue'
import logo from '../assets/logo.png'

const authStore = useAuthStore()
const messageStore = useMessageStore()
const uiStore = useUiStore()
const route = useRoute()
const router = useRouter()

const collapsed = ref(JSON.parse(localStorage.getItem('sidebar_collapsed') || 'false'))
let refreshTimer = null

const routeInfo = {
  '/dashboard': { title: '仪表盘', subtitle: '概览知识库、会话和协作提醒。' },
  '/chat': { title: 'AI 对话', subtitle: '基于检索增强生成的智能知识交互。' },
  '/knowledge': { title: '我的知识库', subtitle: '维护你的专属文档与知识空间。' },
  '/public-knowledge': { title: '公开知识库', subtitle: '浏览并收藏可公开访问的知识空间。' },
  '/messages': { title: '消息通知', subtitle: '查看知识邀请和协作通知。' },
  '/profile': { title: '个人资料', subtitle: '更新头像、昵称和简介。' },
  '/settings': { title: '系统设置', subtitle: '调整默认入库和引用展开策略。' },
  '/admin/users': { title: '用户管理', subtitle: '管理成员权限和账号状态。' },
  '/admin/knowledge': { title: '全局知识库', subtitle: '查看和管理平台级知识空间。' },
}

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/knowledge')) return '/admin/knowledge'
  if (path.startsWith('/admin')) return '/admin/users'
  if (path.startsWith('/knowledge')) return '/knowledge'
  if (path.startsWith('/messages')) return '/messages'
  if (path.startsWith('/public-knowledge')) return '/public-knowledge'
  if (path.startsWith('/settings')) return '/settings'
  if (path.startsWith('/chat')) return '/chat'
  return '/dashboard'
})

const pageTitle = computed(() => routeInfo[activeMenu.value]?.title || route.meta.title || '仪表盘')
const pageSubtitle = computed(() => routeInfo[activeMenu.value]?.subtitle || '欢迎回来，开始你的下一步工作。')

const avatarText = computed(() => {
  const name = authStore.user?.userName || authStore.user?.userAccount || '?'
  return name.charAt(0).toUpperCase()
})

const publicEntries = new Set(['/dashboard', '/chat'])

const toggleSidebar = () => {
  collapsed.value = !collapsed.value
  localStorage.setItem('sidebar_collapsed', JSON.stringify(collapsed.value))
}

const handleMenuSelect = (index) => {
  if (!authStore.isLogin && !publicEntries.has(index)) {
    uiStore.openLogin()
    return
  }
  router.push(index)
}

const goMessages = () => {
  router.push('/messages')
}

const handleCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    await ElMessageBox.confirm('确认退出登录吗？', '提示', { type: 'warning' })
    await authStore.logout()
    messageStore.clear()
    router.push('/dashboard')
  }
}

const syncMessages = async () => {
  if (authStore.isLogin) {
    await messageStore.loadInvitations()
  } else {
    messageStore.clear()
  }
}

watch(
  () => authStore.isLogin,
  () => {
    syncMessages().catch(() => {})
  },
  { immediate: true },
)

watch(collapsed, (value) => {
  localStorage.setItem('sidebar_collapsed', JSON.stringify(value))
})

onMounted(() => {
  refreshTimer = window.setInterval(() => {
    if (authStore.isLogin) {
      messageStore.loadInvitations().catch(() => {})
    }
  }, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.92);
  border-right: 1px solid rgba(226, 232, 240, 0.8);
  backdrop-filter: blur(20px);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 2px 0 16px rgba(15, 23, 42, 0.02);
  z-index: 10;
}

.sidebar-brand {
  min-height: 80px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
}

.brand-logo {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 8px 20px rgba(20, 184, 166, 0.25);
}

.brand-copy {
  min-width: 0;
}

.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.01em;
}

.brand-sub {
  margin-top: 2px;
  font-size: 11px;
  font-weight: 500;
  color: #0d9488;
  background: rgba(20, 184, 166, 0.1);
  padding: 1px 6px;
  border-radius: 6px;
  display: inline-block;
}

.side-menu {
  flex: 1;
  border-right: 0;
  background: transparent;
  padding: 8px 0;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: rgba(20, 184, 166, 0.06);
  --el-menu-active-color: #0f766e;
  --el-menu-text-color: #475569;
}

.side-menu :deep(.el-menu-item) {
  margin: 4px 10px;
  border-radius: 12px;
  height: 46px;
  font-weight: 500;
  transition: all 0.2s ease;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
  font-weight: 600;
}

.side-menu :deep(.el-menu-item:hover) {
  color: #0f766e;
  transform: translateX(2px);
}

.menu-divider-label {
  padding: 14px 20px 6px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #94a3b8;
}

.menu-badge {
  display: inline-flex;
  align-items: center;
  margin-right: 10px;
}

.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px 16px;
  border-top: 1px solid rgba(226, 232, 240, 0.6);
}

.collapse-btn {
  border-color: rgba(226, 232, 240, 0.9);
  color: #64748b;
  transition: all 0.2s ease;
}

.collapse-btn:hover {
  color: #0f766e;
  border-color: #14b8a6;
  background: rgba(20, 184, 166, 0.08);
}

.content-shell {
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 80px;
  padding: 16px 28px;
  background: rgba(255, 255, 255, 0.85);
  border-bottom: 1px solid rgba(226, 232, 240, 0.8);
  backdrop-filter: blur(20px);
  position: sticky;
  top: 0;
  z-index: 9;
}

.title-block {
  min-width: 0;
}

.title-block h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: #0f172a;
  letter-spacing: -0.02em;
}

.title-block p {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.topbar-tools {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.icon-button {
  border: 1px solid rgba(226, 232, 240, 0.9);
  color: #475569;
  transition: all 0.2s ease;
}

.icon-button:hover {
  color: #0f766e;
  border-color: #14b8a6;
  background: rgba(20, 184, 166, 0.08);
}

.locale-chip {
  border-color: rgba(226, 232, 240, 0.9);
  color: #475569;
  font-weight: 600;
  border-radius: 8px;
  background: #f8fafc;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 4px 12px 4px 4px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.95);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.04);
  transition: all 0.2s ease;
}

.user-chip:hover {
  border-color: rgba(20, 184, 166, 0.4);
  box-shadow: 0 4px 12px rgba(20, 184, 166, 0.12);
}

.avatar {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(20, 184, 166, 0.25);
}

.user-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.user-copy strong {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.user-copy small {
  margin-top: 2px;
  font-size: 11px;
  color: #64748b;
}

.chevron {
  color: #94a3b8;
  font-size: 12px;
  transition: transform 0.2s ease;
}

.page-body {
  min-width: 0;
  padding: 24px 28px 36px;
}

@media (max-width: 1024px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
    padding: 14px 20px;
  }
  .page-body {
    padding: 16px 20px;
  }
}
</style>