<template>
  <el-container class="app-shell">
    <el-aside class="sidebar" :width="collapsed ? '92px' : '264px'">
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
          <span>对话</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Folder /></el-icon>
          <span>知识库</span>
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
          <span>消息</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>设置</span>
        </el-menu-item>
        <template v-if="authStore.isAdmin">
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
          <el-button circle :icon="collapsed ? Expand : Fold" @click="toggleSidebar" />
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
                <el-avatar :size="32" class="avatar" :src="authStore.user?.userAvatar || undefined">
                  {{ avatarText }}
                </el-avatar>
                <span class="user-copy">
                  <strong>{{ authStore.user?.userName || authStore.user?.userAccount }}</strong>
                  <small>{{ authStore.isAdmin ? '管理员' : '成员' }}</small>
                </span>
                <el-icon class="chevron"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
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
            <el-button type="primary" plain :icon="User" @click="uiStore.openLogin()">登录</el-button>
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
  '/chat': { title: '对话', subtitle: '在知识库和通用模式之间快速切换。' },
  '/knowledge': { title: '知识库', subtitle: '维护你的专属知识空间。' },
  '/public-knowledge': { title: '公开知识库', subtitle: '浏览并收藏可公开访问的知识空间。' },
  '/messages': { title: '消息中心', subtitle: '查看知识邀请和协作通知。' },
  '/profile': { title: '个人资料', subtitle: '更新头像、昵称和简介。' },
  '/settings': { title: '设置', subtitle: '调整默认入库和引用展开策略。' },
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

const publicEntries = new Set(['/dashboard', '/chat', '/public-knowledge'])

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
  border-right: 1px solid rgba(148, 163, 184, 0.18);
  backdrop-filter: blur(18px);
}

.sidebar-brand {
  min-height: 88px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.brand-logo {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 14px 30px rgba(20, 184, 166, 0.22);
}

.brand-copy {
  min-width: 0;
}

.brand-name {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.brand-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

.side-menu {
  flex: 1;
  border-right: 0;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: rgba(20, 184, 166, 0.08);
  --el-menu-active-color: #0f766e;
  --el-menu-text-color: #475569;
}

.side-menu :deep(.el-menu-item) {
  margin: 6px 12px;
  border-radius: 16px;
  height: 50px;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
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
  padding: 14px 16px 18px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.content-shell {
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-height: 88px;
  padding: 18px 24px;
  background: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  backdrop-filter: blur(18px);
}

.title-block {
  min-width: 0;
}

.title-block h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  color: #0f172a;
}

.title-block p {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.topbar-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.icon-button {
  border: 1px solid rgba(148, 163, 184, 0.2);
  color: #475569;
}

.locale-chip {
  border-color: rgba(148, 163, 184, 0.18);
  color: #334155;
  border-radius: 999px;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 4px 10px 4px 4px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  cursor: pointer;
}

.avatar {
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  color: #fff;
  font-weight: 600;
}

.user-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.1;
}

.user-copy strong {
  font-size: 13px;
  color: #0f172a;
}

.user-copy small {
  margin-top: 2px;
  font-size: 11px;
  color: #64748b;
}

.chevron {
  color: #94a3b8;
}

.page-body {
  min-width: 0;
  padding: 22px 24px 28px;
}

@media (max-width: 1024px) {
  .topbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
