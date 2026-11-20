<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { setTheme, getTheme, THEMES } from '../theme'
import { toast } from '../components/toast'
import XIcon from '../components/XIcon.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const collapsed = ref(false)
const showUserMenu = ref(false)
const currentTheme = ref(getTheme())

const navs = [
  { path: '/knowledge', icon: 'folder', label: '我的知识库' },
  { path: '/chat', icon: 'chatDotRound', label: 'AI 问答' },
  { path: '/settings', icon: 'setting', label: '系统设置' },
]

function toggleSidebar() {
  collapsed.value = !collapsed.value
}

function pickTheme(key) {
  setTheme(key)
  currentTheme.value = key
  toast.success(`已切换为「${THEMES.find((t) => t.key === key)?.name}」主题`)
}

function onDocClick(e) {
  if (!e.target.closest('.user-area')) showUserMenu.value = false
}
onMounted(() => {
  document.addEventListener('click', onDocClick)
  // 40100 → 跳登录（request.js 派发）
  window.addEventListener('auth:expired', () => router.push({ name: 'login' }))
})
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))

async function handleLogout() {
  await auth.logout()
  showUserMenu.value = false
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ collapsed }">
      <div class="side-logo">
        <span class="mark" />
        <b v-if="!collapsed">学智汇</b>
      </div>

      <nav class="side-nav">
        <router-link
          v-for="n in navs" :key="n.path" :to="n.path"
          class="nav-item" :class="{ active: route.path.startsWith(n.path) }"
        >
          <span class="ic-box"><XIcon :name="n.icon" :size="16" /></span>
          <span v-if="!collapsed">{{ n.label }}</span>
        </router-link>
      </nav>

      <div class="side-foot">
        <div class="user-area me" @click="showUserMenu = !showUserMenu">
          <span class="avatar">{{ auth.avatarText }}</span>
          <span v-if="!collapsed" class="me-copy">
            <b>{{ auth.user?.userName || auth.user?.userAccount }}</b>
            <small class="role"><i class="r-dot" />{{ auth.isAdmin ? '管理员' : '成员' }}</small>
          </span>
          <XIcon v-if="!collapsed" name="arrowDown" :size="13" class="chev" />
        </div>
        <div v-if="showUserMenu" class="user-pop">
          <div class="pop-title">外观主题</div>
          <button
            v-for="t in THEMES" :key="t.key" class="pop-theme"
            :class="{ on: currentTheme === t.key }" @click="pickTheme(t.key)"
          >
            <span v-for="(s, i) in t.swatches" :key="i" class="sw" :style="{ background: s }" />
            {{ t.name }}
            <XIcon v-if="currentTheme === t.key" name="circleCheckFilled" :size="14" class="ck" />
          </button>
          <div class="pop-divider" />
          <button class="pop-item" @click="handleLogout">
            <XIcon name="switchButton" :size="14" /> 退出登录
          </button>
        </div>
        <button class="collapse-btn" :title="collapsed ? '展开侧栏' : '收起侧栏'" @click="toggleSidebar">
          <XIcon :name="collapsed ? 'expand' : 'fold'" :size="14" />
        </button>
      </div>
    </aside>

    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell { display: grid; grid-template-columns: auto 1fr; min-height: 100vh; }

.sidebar {
  width: 240px; display: flex; flex-direction: column;
  background: linear-gradient(180deg, var(--side1), var(--side2));
  border-right: 1px solid var(--line);
  padding: 22px 14px 16px;
  position: sticky; top: 0; height: 100vh;
  transition: width .18s ease;
}
.sidebar.collapsed { width: 76px; }
.side-logo { display: flex; align-items: center; gap: 10px; padding: 0 10px 20px; }
.side-logo .mark { width: 32px; height: 32px; border-radius: 50%; background: var(--green); display: flex; align-items: center; justify-content: center; position: relative; flex: none; }
.side-logo .mark::after { content: ""; width: 10px; height: 10px; border-radius: 50%; background: var(--bright); border: 2.5px solid var(--surface); }
.side-logo b { font-size: 16px; letter-spacing: 2px; }

.side-nav { display: flex; flex-direction: column; gap: 2px; }
.nav-item {
  display: flex; align-items: center; gap: 10px; padding: 9px 10px;
  border-radius: 10px; font-size: 14px; color: var(--ink-soft);
  text-decoration: none;
}
.nav-item:hover { background: color-mix(in srgb, var(--surface) 60%, transparent); text-decoration: none; }
.nav-item.active {
  background: var(--surface); color: var(--green-dark); font-weight: 700;
  box-shadow: 0 2px 10px color-mix(in srgb, var(--green) 10%, transparent);
}
.nav-item .ic-box {
  width: 28px; height: 28px; border-radius: 50%; flex: none;
  display: flex; align-items: center; justify-content: center; color: var(--muted);
}
.nav-item.active .ic-box { background: var(--mint); color: var(--green-dark); }
.nav-item span:last-child { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.side-foot { margin-top: auto; border-top: 1px dashed var(--mint-deep); padding-top: 14px; position: relative; }
.me { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 10px; cursor: pointer; }
.me:hover { background: color-mix(in srgb, var(--surface) 60%, transparent); }
.avatar {
  width: 34px; height: 34px; border-radius: 50%; flex: none;
  background: linear-gradient(135deg, var(--green), var(--bright));
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-size: 13px; font-weight: 700; border: 2px solid var(--surface);
}
.me-copy { display: flex; flex-direction: column; min-width: 0; }
.me-copy b { font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.role { font-size: 11px; color: var(--blue-ink); display: flex; align-items: center; gap: 4px; }
.r-dot { width: 5px; height: 5px; border-radius: 50%; background: var(--blue); }
.chev { margin-left: auto; color: var(--muted); }

.user-pop {
  position: absolute; bottom: 52px; left: 8px; right: 8px; z-index: 30;
  background: var(--surface); border: 1px solid var(--line); border-radius: 12px;
  box-shadow: 0 12px 34px color-mix(in srgb, var(--ink) 22%, transparent);
  padding: 8px;
}
.pop-title { font-size: 11px; color: var(--muted); padding: 6px 10px; letter-spacing: 1px; }
.pop-theme {
  display: flex; align-items: center; gap: 6px; width: 100%;
  padding: 8px 10px; border-radius: 8px; font-size: 13px; color: var(--ink); text-align: left;
}
.pop-theme:hover { background: var(--mint); }
.pop-theme .sw { width: 12px; height: 12px; border-radius: 50%; }
.pop-theme .ck { margin-left: auto; color: var(--green); }
.pop-theme.on { background: var(--mint); color: var(--green-dark); font-weight: 600; }
.pop-divider { height: 1px; background: var(--line); margin: 6px 4px; }
.pop-item { display: flex; align-items: center; gap: 8px; width: 100%; padding: 8px 10px; border-radius: 8px; font-size: 13px; color: var(--ink); }
.pop-item:hover { background: #FBEFEE; color: #C0453B; }

.collapse-btn {
  margin: 10px auto 0; width: 32px; height: 32px; border-radius: 50%;
  border: 1px solid var(--line); background: var(--surface); color: var(--muted);
  display: flex; align-items: center; justify-content: center;
}
.collapse-btn:hover { color: var(--green-dark); border-color: var(--green); }

.content { min-width: 0; }
</style>
