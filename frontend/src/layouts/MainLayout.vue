<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">
        <img class="logo-img" :src="logo" alt="学智汇" />
        <span>学智汇</span>
      </div>
      <el-menu :default-active="activeMenu" class="menu" @select="handleMenuSelect">
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
          <el-badge :value="messageStore.unreadCount" :hidden="messageStore.unreadCount === 0" class="menu-badge">
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
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-right">
          <template v-if="authStore.isLogin">
            <el-dropdown @command="handleCommand">
              <span class="user-info">
                <el-avatar :size="30" class="avatar" :src="authStore.user?.userAvatar || undefined">{{ avatarText }}</el-avatar>
                <span class="username">{{ authStore.user?.userName || authStore.user?.userAccount }}</span>
                <el-tag v-if="authStore.isAdmin" size="small" type="warning" class="role-tag">管理员</el-tag>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <el-icon><User /></el-icon>我的
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <span class="login-trigger" @click="uiStore.openLogin()">
              <el-icon><User /></el-icon>
              <span>未登录</span>
            </span>
          </template>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 登录弹窗 -->
    <LoginDialog v-model="uiStore.loginDialogVisible" />
  </el-container>
</template>

<script setup>
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Bell, ChatDotRound, Collection, Compass, Folder, Setting, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useMessageStore } from '../stores/messages'
import { useUiStore } from '../stores/ui'
import LoginDialog from '../components/LoginDialog.vue'
import logo from '../assets/logo.png'

const authStore = useAuthStore()
const messageStore = useMessageStore()
const uiStore = useUiStore()
const router = useRouter()
let refreshTimer = null

const activeMenu = computed(() => {
  const path = router.currentRoute.value.path
  if (path.startsWith('/admin')) return path
  if (path.startsWith('/chat')) return '/chat'
  if (path.startsWith('/messages')) return '/messages'
  if (path.startsWith('/knowledge')) return '/knowledge'
  return path
})

const avatarText = computed(() => {
  const name = authStore.user?.userName || authStore.user?.userAccount || '?'
  return name.charAt(0).toUpperCase()
})

const handleMenuSelect = (index) => {
  // 访客只开放对话页；点知识库等需登录的入口 → 弹登录窗
  if (!authStore.isLogin && index !== '/chat') {
    uiStore.openLogin()
    return
  }
  router.push(index)
}

const handleCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await authStore.logout()
    messageStore.clear()
    router.push('/chat')
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
.layout {
  height: 100vh;
}
.aside {
  background: #1d2129;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}
.logo-img {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  object-fit: contain;
}
.menu {
  border-right: none;
  background: transparent;
  --el-menu-text-color: #cfd3dc;
  --el-menu-hover-bg-color: #2a2f3a;
  --el-menu-active-color: #409eff;
}
.menu-badge {
  display: inline-flex;
  align-items: center;
  margin-right: 8px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fff;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.avatar {
  background: #409eff;
  color: #fff;
  font-weight: 600;
}
.username {
  font-size: 14px;
  color: #303133;
}
.login-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  transition: all 0.2s;
  outline: none;
}
.login-trigger:hover {
  color: #409eff;
  border-color: #409eff;
}
.main {
  background: #f5f7fa;
  padding: 20px;
}
</style>
