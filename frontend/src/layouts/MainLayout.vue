<template>
  <el-container class="app-shell">
    <el-aside class="sidebar" :class="{ 'sidebar--compact': compactNavigation && sidebarCollapsed }" :width="sidebarWidth">
      <div class="sidebar-brand">
        <img class="brand-logo" :src="logo" alt="学智汇" />
        <div v-if="!sidebarCollapsed" class="brand-copy">
          <div class="brand-name">学智汇</div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        class="side-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/dashboard" aria-label="仪表盘">
          <el-icon><House /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/chat" aria-label="AI 对话">
          <el-icon><ChatDotRound /></el-icon>
          <template #title>AI 对话</template>
        </el-menu-item>
        <el-menu-item index="/knowledge" aria-label="我的知识库">
          <el-icon><Folder /></el-icon>
          <template #title>我的知识库</template>
        </el-menu-item>
        <el-menu-item index="/public-knowledge" aria-label="公开知识库">
          <el-icon><Compass /></el-icon>
          <template #title>公开知识库</template>
        </el-menu-item>
        <el-menu-item index="/vector-chunks" aria-label="切片查询">
          <el-icon><Document /></el-icon>
          <template #title>切片查询</template>
        </el-menu-item>
        <el-menu-item index="/wiki" aria-label="AI Wiki">
          <el-icon><Reading /></el-icon>
          <template #title>AI Wiki</template>
        </el-menu-item>
        <el-menu-item index="/retrieval-test" aria-label="检索测试">
          <el-icon><Search /></el-icon>
          <template #title>检索测试</template>
        </el-menu-item>
        <el-menu-item index="/messages" :aria-label="messagesLabel">
          <el-icon><Bell /></el-icon>
          <template #title>
            <span>消息通知</span>
            <span v-if="!sidebarCollapsed && messageStore.unreadCount > 0" class="menu-count" aria-hidden="true">
              {{ messageStore.unreadCount > 99 ? '99+' : messageStore.unreadCount }}
            </span>
          </template>
        </el-menu-item>
        <el-menu-item index="/settings" aria-label="系统设置">
          <el-icon><Setting /></el-icon>
          <template #title>系统设置</template>
        </el-menu-item>
        <template v-if="authStore.isAdmin">
          <div v-if="!sidebarCollapsed" class="menu-divider-label">平台管理</div>
          <el-menu-item index="/admin/users" aria-label="用户管理">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/knowledge" aria-label="全局知识库">
            <el-icon><Collection /></el-icon>
            <template #title>全局知识库</template>
          </el-menu-item>
        </template>
      </el-menu>

      <el-tooltip :content="sidebarLabel" placement="top">
        <el-button
          class="collapse-btn"
          circle
          :icon="sidebarCollapsed ? Expand : Fold"
          :aria-label="sidebarLabel"
          :aria-expanded="!sidebarCollapsed"
          @click="toggleSidebar"
        />
      </el-tooltip>
    </el-aside>

    <el-container class="content-shell">
      <el-header class="topbar">
        <div class="title-block">
          <h1>{{ pageTitle }}</h1>
        </div>

        <div class="topbar-tools">
          <el-badge is-dot :hidden="messageStore.unreadCount === 0" class="topbar-badge">
            <el-button class="icon-button" circle :icon="Bell" :aria-label="messagesLabel" @click="goMessages" />
          </el-badge>

          <template v-if="authStore.isLogin">
            <el-dropdown @command="handleCommand">
              <span class="user-chip">
                <el-avatar :size="34" class="avatar" :src="authStore.user?.userAvatar || undefined">
                  {{ avatarText }}
                </el-avatar>
                <span class="user-copy">
                  <strong>{{ authStore.user?.userName || authStore.user?.userAccount }}</strong>
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
        </div>
      </el-header>

      <el-main class="page-body">
        <router-view v-slot="{ Component, route: pageRoute }">
          <KeepAlive v-if="authStore.isLogin" :key="authStore.user?.id" :max="6">
            <component :is="Component" v-if="pageRoute.meta.keepTab" :key="pageRoute.name" />
          </KeepAlive>
          <component
            :is="Component"
            v-if="!pageRoute.meta.keepTab"
            :key="pageRoute.name === 'KnowledgeWiki' ? pageRoute.params.id : undefined"
          />
        </router-view>
      </el-main>
    </el-container>
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
  Document,
  Expand,
  Fold,
  Folder,
  House,
  Reading,
  Search,
  Setting,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useMessageStore } from '../stores/messages'
import logo from '../assets/logo.png'

const authStore = useAuthStore()
const messageStore = useMessageStore()
const route = useRoute()
const router = useRouter()

const collapsed = ref(JSON.parse(localStorage.getItem('sidebar_collapsed') || 'false'))
const compactExpanded = ref(false)
const compactNavigation = computed(() => route.name === 'VectorChunks' || route.name === 'Wiki')
const sidebarCollapsed = computed(() => compactNavigation.value ? !compactExpanded.value : collapsed.value)
const sidebarWidth = computed(() => {
  if (!sidebarCollapsed.value) return '200px'
  return compactNavigation.value ? '56px' : '72px'
})
const sidebarLabel = computed(() => sidebarCollapsed.value ? '展开侧栏' : '收起侧栏')
let refreshTimer = null

const routeInfo = {
  '/dashboard': { title: '仪表盘' },
  '/chat': { title: 'AI 对话' },
  '/knowledge': { title: '我的知识库' },
  '/public-knowledge': { title: '公开知识库' },
  '/vector-chunks': { title: '切片查询' },
  '/wiki': { title: 'AI Wiki' },
  '/retrieval-test': { title: '检索测试' },
  '/messages': { title: '消息通知' },
  '/profile': { title: '个人资料' },
  '/settings': { title: '系统设置' },
  '/admin/users': { title: '用户管理' },
  '/admin/knowledge': { title: '全局知识库' },
}

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/admin/knowledge')) return '/admin/knowledge'
  if (path.startsWith('/admin')) return '/admin/users'
  // /profile 不在侧栏菜单里：返回空串让菜单无高亮项，标题走 route.meta.title（此前回落到 /dashboard，
  // 导致个人资料页顶栏显示"仪表盘"、侧栏高亮错位）
  if (path.startsWith('/profile')) return ''
  if (path.startsWith('/knowledge')) return '/knowledge'
  if (path.startsWith('/messages')) return '/messages'
  if (path.startsWith('/public-knowledge')) return '/public-knowledge'
  if (path.startsWith('/settings')) return '/settings'
  if (path.startsWith('/chat')) return '/chat'
  if (route.name === 'VectorChunks') return '/vector-chunks'
  if (route.name === 'Wiki') return '/wiki'
  if (route.name === 'RetrievalTest') return '/retrieval-test'
  return '/dashboard'
})

const pageTitle = computed(() => routeInfo[activeMenu.value]?.title || route.meta.title || '仪表盘')
const messagesLabel = computed(() => messageStore.unreadCount > 0
  ? `消息通知，${messageStore.unreadCount} 条未读`
  : '消息通知')

const avatarText = computed(() => {
  const name = authStore.user?.userName || authStore.user?.userAccount || '?'
  return name.charAt(0).toUpperCase()
})

const toggleSidebar = () => {
  if (compactNavigation.value) {
    compactExpanded.value = !compactExpanded.value
    return
  }
  collapsed.value = !collapsed.value
}

const handleMenuSelect = (index) => {
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
    router.push('/login')
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
watch(compactNavigation, () => { compactExpanded.value = false })

onMounted(() => {
  if (authStore.isLogin) authStore.fetchCurrentUser().catch(() => {})
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
  height: 100vh;
}

.sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--card);
  border-right: 1px solid var(--line);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 10;
}

.sidebar-brand {
  min-height: 64px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--line);
}

.sidebar--compact .sidebar-brand { justify-content: center; padding-inline: 0; }
.sidebar--compact .side-menu { width: 100%; }
.sidebar--compact .side-menu :deep(.el-menu-item) { height: 40px; }
.sidebar--compact .side-menu :deep(.el-menu-tooltip__trigger) { justify-content: center; padding-inline: 0; }
.sidebar--compact .collapse-btn { left: 50%; transform: translateX(-50%); }

.brand-logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}

.brand-copy {
  min-width: 0;
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--t1);
  letter-spacing: -0.01em;
}

.side-menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border-right: 0;
  background: transparent;
  padding: 8px 0 64px;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: var(--tint);
  --el-menu-active-color: var(--brand);
  --el-menu-text-color: var(--t2);
}

.side-menu :deep(.el-menu-item) {
  position: relative;
  margin: 3px 8px;
  border-radius: var(--r3);
  height: 38px;
  font-size: var(--text-body);
  font-weight: 500;
  transition: background-color 0.15s ease-out, color 0.15s ease-out;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: var(--tint);
  color: var(--brand);
  font-weight: 600;
}

/* 激活项左侧指示条：高亮位置一眼可辨（同组内切换由 View Transitions 负责平滑滑动） */
.side-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 9px;
  bottom: 9px;
  width: 3px;
  border-radius: 2px;
  background: var(--brand);
}

.side-menu :deep(.el-menu-item:hover) {
  color: var(--brand);
}

.menu-divider-label {
  padding: 14px 20px 4px;
  font-size: var(--text-muted);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--t4);
}

.side-menu:not(.el-menu--collapse) :deep(.el-menu-item) {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  padding-inline: 12px;
}

.side-menu:not(.el-menu--collapse) :deep(.el-menu-item > .el-icon) {
  margin: 0;
  justify-self: center;
  font-size: 18px;
}

.menu-count {
  min-width: 18px;
  padding-inline: 5px;
  border-radius: var(--r3);
  background: var(--tint);
  color: var(--brand);
  font-size: var(--text-muted);
  line-height: 18px;
  text-align: center;
}

/* 折叠前后使用同一左侧锚点。 */
.collapse-btn {
  position: absolute;
  left: 20px;
  bottom: 14px;
  z-index: 20;
  background: var(--card);
  border-color: var(--line);
  color: var(--t3);
  transition: color 0.15s ease-out, border-color 0.15s ease-out, background 0.15s ease-out;
}

.collapse-btn:hover {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--tint);
}

.content-shell {
  --topbar-height: 60px;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  height: var(--topbar-height);
  padding: 0 20px;
  background: var(--card);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 9;
}

.title-block {
  min-width: 0;
}

.title-block h1 {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  line-height: 1.2;
  color: var(--t1);
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topbar-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
  justify-content: flex-end;
  flex-shrink: 0;
}

.icon-button {
  border: 1px solid var(--line);
  color: var(--t3);
  transition: all 0.15s ease-out;
}

.icon-button:hover {
  color: var(--brand);
  border-color: var(--brand);
  background: var(--tint);
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 3px 10px 3px 4px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--card);
  cursor: pointer;
  transition: border-color 0.15s ease-out;
}

.user-chip:hover {
  border-color: var(--brand);
}

.avatar {
  background: var(--brand);
  color: #fff;
  font-weight: 600;
}

.user-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.user-copy strong {
  font-size: 13px;
  font-weight: 600;
  color: var(--t1);
}

.chevron {
  color: var(--t4);
  font-size: 12px;
  transition: transform 0.2s ease;
}

.topbar-badge :deep(.el-badge__content.is-fixed) {
  top: 5px;
  right: 5px;
  transform: none;
  background: var(--brand);
}

.user-copy strong {
  max-width: 144px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-body {
  --page-body-padding-top: 18px;
  --page-body-padding-bottom: 28px;
  min-width: 0;
  padding: var(--page-body-padding-top) 24px var(--page-body-padding-bottom);
  container: page-body / inline-size;
}

@media (max-width: 1024px) {
  .topbar { gap: 8px; padding-inline: 12px; }
  .page-body {
    --page-body-padding-top: 14px;
    --page-body-padding-bottom: 20px;
    padding-inline: 12px;
  }
}

@media (max-width: 640px) {
  .app-shell { height: auto; min-height: 100dvh; flex-direction: column; }
  .sidebar { width: 100%; height: auto; border-right: 0; border-bottom: 1px solid var(--line); }
  .sidebar-brand { min-height: 48px; padding-block: 6px; }
  .sidebar--compact .sidebar-brand { justify-content: flex-start; padding-inline: 16px; }
  .side-menu { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 4px 0; }
  .menu-divider-label { grid-column: 1 / -1; padding-top: 8px; }
  .collapse-btn { top: 8px; right: 12px; bottom: auto; left: auto; }
  .sidebar--compact .collapse-btn { left: auto; transform: none; }
  .side-menu.el-menu--collapse { display: none; }
  .user-copy strong { max-width: 88px; }
  .page-body:has(.wiki-page) {
    --wiki-outline-top: var(--topbar-height);
    overflow: visible;
  }
}
</style>
