<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">
        <span class="logo-icon">学</span>
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
                <el-avatar :size="30" class="avatar">{{ avatarText }}</el-avatar>
                <span class="username">{{ authStore.user?.userName || authStore.user?.userAccount }}</span>
                <el-tag v-if="authStore.isAdmin" size="small" type="warning" class="role-tag">管理员</el-tag>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="logout">退出登录</el-dropdown-item>
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
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import LoginDialog from '../components/LoginDialog.vue'

const authStore = useAuthStore()
const uiStore = useUiStore()
const router = useRouter()

const activeMenu = computed(() => {
  const path = router.currentRoute.value.path
  if (path.startsWith('/admin')) return path
  if (path.startsWith('/chat')) return '/chat'
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
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await authStore.logout()
    router.push('/chat')
  }
}
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
.logo-icon {
  width: 28px;
  height: 28px;
  line-height: 28px;
  text-align: center;
  border-radius: 6px;
  background: #409eff;
  color: #fff;
  font-size: 15px;
}
.menu {
  border-right: none;
  background: transparent;
  --el-menu-text-color: #cfd3dc;
  --el-menu-hover-bg-color: #2a2f3a;
  --el-menu-active-color: #409eff;
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
