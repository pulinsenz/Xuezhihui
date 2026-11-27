<template>
  <div class="admin-page">
    <div class="page-head">
      <div class="head-left">
        <h2 class="page-title">系统用户管理</h2>
      </div>
      <div class="head-stat-pill">
        <span class="stat-label">总注册用户</span>
        <strong class="stat-value">{{ total }}</strong>
      </div>
    </div>

    <section class="admin-card">
      <div class="toolbar">
        <div class="toolbar-search-group">
          <el-input
            v-model="keyword"
            placeholder="搜索用户账号 / 昵称..."
            clearable
            class="search-input"
            aria-label="搜索账号或昵称"
            :prefix-icon="Search"
            @keyup.enter="loadUsers(1)"
            @clear="loadUsers(1)"
          />
          <el-select
            v-model="deletedFilter"
            placeholder="账号状态"
            class="filter-select"
            @change="loadUsers(1)"
          >
            <el-option label="全部状态" :value="null" />
            <el-option label="正常账号" :value="0" />
            <el-option label="已注销/删除" :value="1" />
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-button type="primary" :icon="Search" class="btn-brand" @click="loadUsers(1)">查询数据</el-button>
          <el-button :icon="Refresh" @click="loadUsers(1)">刷新</el-button>
        </div>
      </div>

      <div class="table-shell">
        <el-table :data="users" stripe v-loading="loading" class="custom-admin-table">
          <el-table-column prop="userAccount" label="登录账号" min-width="150">
            <template #default="{ row }">
              <div class="account-cell">
                <el-avatar :size="24" class="table-avatar">{{ (row.userName || row.userAccount || '?').charAt(0).toUpperCase() }}</el-avatar>
                <span class="user-account-text">{{ row.userAccount }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="userName" label="用户昵称" min-width="140">
            <template #default="{ row }">
              <span class="user-name-text">{{ row.userName || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="系统角色" width="130">
            <template #default="{ row }">
              <el-tag
                :type="row.userRole === ROLE.SUPER_ADMIN ? 'primary' : row.userRole === ROLE.ADMIN ? 'warning' : 'info'"
                size="small"
                effect="plain"
                class="role-pill"
              >
                {{ roleLabel(row.userRole) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="账号状态" width="110">
            <template #default="{ row }">
              <span class="status-badge" :class="row.isDelete === 1 ? 'is-deleted' : 'is-normal'">
                <span class="status-dot" />
                {{ row.isDelete === 1 ? '已禁用' : '正常运作' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="注册时间" width="180">
            <template #default="{ row }">
              <span class="time-text">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作管控" width="160" align="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button
                  size="small"
                  plain
                  :disabled="isSelf(row)"
                  @click="userDetail = row"
                >
                  {{ isSelf(row) ? '当前本人' : '查看' }}
                </el-button>

                <el-dropdown v-if="hasMoreActions(row)" trigger="click" @command="handleRowCommand($event, row)">
                  <el-button size="small" class="more-btn" :aria-label="`${row.userAccount} 的更多操作`">
                    <span>更多</span>
                    <el-icon class="more-icon"><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canChangeRole(row)" command="role" :icon="Edit">
                        调整为「{{ roleLabel(nextRole(row)) }}」
                      </el-dropdown-item>
                      <el-dropdown-item v-if="canDelete(row)" command="delete" :icon="Delete" class="danger-action">
                        禁用/删除账号
                      </el-dropdown-item>
                      <el-dropdown-item v-if="canRestore(row)" command="restore" :icon="RefreshLeft">
                        恢复账号正常
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button v-else size="small" disabled class="empty-action">—</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="custom-pagination"
          @current-change="loadUsers()"
          @size-change="loadUsers(1)"
        />
      </div>
    </section>

    <!-- 账号详细信息抽屉/弹窗 -->
    <el-dialog
      :model-value="Boolean(userDetail)"
      title="用户详情信息"
      width="min(460px, calc(100vw - 32px))"
      class="custom-dialog"
      @close="userDetail = null"
    >
      <div v-if="userDetail" class="detail-container">
        <div class="detail-avatar-row">
          <el-avatar :size="50" class="detail-avatar">{{ (userDetail.userName || userDetail.userAccount || '?').charAt(0).toUpperCase() }}</el-avatar>
          <div class="detail-names">
            <h4>{{ userDetail.userName || '未设置昵称' }}</h4>
            <span>{{ userDetail.userAccount }}</span>
          </div>
        </div>
        <dl class="user-detail-list">
          <dt>用户 ID</dt><dd>{{ userDetail.id }}</dd>
          <dt>登录账号</dt><dd><code>{{ userDetail.userAccount }}</code></dd>
          <dt>系统权限角色</dt><dd>{{ roleLabel(userDetail.userRole) }}</dd>
          <dt>当前可用状态</dt><dd>{{ userDetail.isDelete === 1 ? '已禁用 / 已注销' : '正常在用' }}</dd>
          <dt>注册入库时间</dt><dd>{{ userDetail.createTime?.replace('T', ' ').slice(0, 19) }}</dd>
        </dl>
      </div>
      <template #footer>
        <el-button @click="userDetail = null">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 角色权限调整弹窗 -->
    <el-dialog
      v-model="roleDialogVisible"
      :title="roleDialogTitle"
      width="min(460px, calc(100vw - 32px))"
      class="custom-dialog"
      :close-on-click-modal="false"
    >
      <div class="confirm-box">
        <p class="confirm-prompt">
          确定将用户「<strong class="highlight-user">{{ roleTarget?.userAccount }}</strong>」的身份调整为
          <strong class="highlight-role">「{{ roleLabel(nextRole(roleTarget)) }}」</strong>吗？
        </p>
        <p class="confirm-note">权限更新后将立即生效，该用户下次请求时即享有对应角色功能权限。</p>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleLoading" class="btn-brand" @click="confirmRole">确定修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, MoreFilled, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { listUsers, updateUserRole, deleteUser, restoreUser } from '../api/admin'
import { useAuthStore } from '../stores/auth'
import { ROLE, roleLabel } from '../utils/roles'

const authStore = useAuthStore()

const keyword = ref('')
const deletedFilter = ref(0)
const users = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const userDetail = ref(null)

const roleDialogVisible = ref(false)
const roleTarget = ref(null)
const roleLoading = ref(false)
const roleDialogTitle = ref('')

const loadUsers = async (page) => {
  if (page) pageNum.value = page
  loading.value = true
  try {
    const data = await listUsers({
      keyword: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      deleted: deletedFilter.value,
    })
    users.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const isSelf = (row) => row.id === authStore.user?.id
const canChangeRole = (row) => row.isDelete !== 1 && authStore.isSuperAdmin && !isSelf(row)
  && (row.userRole === ROLE.ADMIN || row.userRole === ROLE.USER)
const canDelete = (row) => row.isDelete !== 1 && !isSelf(row) && row.userRole !== ROLE.SUPER_ADMIN
const canRestore = (row) => row.isDelete === 1 && !isSelf(row)
const hasMoreActions = (row) => canChangeRole(row) || canDelete(row) || canRestore(row)
const nextRole = (row) => row?.userRole === ROLE.ADMIN ? ROLE.USER : ROLE.ADMIN

const openRoleDialog = (row) => {
  roleTarget.value = row
  roleDialogTitle.value = `调整用户角色 - ${row.userAccount}`
  roleDialogVisible.value = true
}

const confirmRole = async () => {
  if (!roleTarget.value || !canChangeRole(roleTarget.value)) return
  const targetRole = nextRole(roleTarget.value)
  roleLoading.value = true
  try {
    await updateUserRole(roleTarget.value.id, { userRole: targetRole })
    ElMessage.success('角色已更新，实时生效')
    roleDialogVisible.value = false
    loadUsers()
  } finally {
    roleLoading.value = false
  }
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除用户「${row.userAccount}」吗？其账号将立即失效。`, '删除确认', {
    type: 'warning',
  })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  loadUsers()
}

const handleRestore = async (row) => {
  await ElMessageBox.confirm(`确定恢复用户「${row.userAccount}」吗？恢复后其可重新登录。`, '恢复确认', {
    type: 'info',
  })
  await restoreUser(row.id)
  ElMessage.success('已恢复')
  loadUsers()
}

const handleRowCommand = async (command, row) => {
  if (command === 'role' && canChangeRole(row)) openRoleDialog(row)
  if (command === 'delete' && canDelete(row)) await handleDelete(row)
  if (command === 'restore' && canRestore(row)) await handleRestore(row)
}

onMounted(loadUsers)
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.6vw, 20px);
  width: 100%;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.head-left {
  display: flex;
  flex-direction: column;
}

.page-title {
  margin: 2px 0 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.head-stat-pill {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 16px;
  border-radius: var(--r3);
  background: var(--card);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-card);
}

.stat-label {
  font-size: var(--text-muted);
  color: var(--t3);
}

.stat-value {
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}

.admin-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
  padding: clamp(14px, 1.8vw, 20px);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.toolbar-search-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input {
  width: min(100%, clamp(220px, 20vw, 300px));
}

.search-input :deep(.el-input__wrapper),
.filter-select :deep(.el-input__wrapper) {
  border-radius: var(--r3);
}

.filter-select {
  width: clamp(130px, 14vw, 160px);
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-brand {
  background: var(--brand);
  border-color: var(--brand);
  border-radius: var(--r3);
  font-weight: 600;
}

.btn-brand:hover {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
}

.table-shell {
  border-radius: var(--r3);
  overflow: hidden;
  border: 1px solid var(--line);
}

.account-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-avatar {
  background: var(--brand);
  color: var(--card);
  font-size: var(--text-muted);
  font-weight: 700;
  flex-shrink: 0;
}

.user-account-text {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
}

.user-name-text {
  font-size: var(--text-body);
  color: var(--t2);
}

.role-pill {
  border-radius: var(--r3);
  font-weight: 600;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--text-muted);
  font-weight: 500;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.status-badge.is-normal {
  color: var(--el-color-success-dark-2);
}
.status-badge.is-normal .status-dot {
  background: var(--el-color-success);
}

.status-badge.is-deleted {
  color: var(--el-color-danger);
}
.status-badge.is-deleted .status-dot {
  background: var(--el-color-danger);
}

.time-text {
  font-size: var(--text-muted);
  color: var(--t3);
  font-variant-numeric: tabular-nums;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.row-actions :deep(.el-button) {
  min-height: 28px;
  border-radius: var(--r3);
}

.more-icon {
  margin-left: 2px;
  font-size: var(--text-muted);
}

.danger-action {
  color: var(--el-color-danger);
}

.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.custom-pagination :deep(.el-pagination__sizes .el-input__wrapper) {
  border-radius: var(--r3);
}

/* 详情抽屉卡片 */
.detail-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-avatar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.detail-avatar {
  background: var(--brand);
  color: var(--card);
  font-weight: 700;
  font-size: var(--text-heading);
}

.detail-names h4 {
  margin: 0;
  font-size: var(--text-heading);
  color: var(--t1);
}

.detail-names span {
  font-size: var(--text-muted);
  color: var(--t3);
  margin-top: 2px;
  display: block;
}

.user-detail-list {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 10px 14px;
  margin: 0;
  font-size: var(--text-body);
}

.user-detail-list dt {
  color: var(--t3);
}

.user-detail-list dd {
  margin: 0;
  color: var(--t1);
  font-weight: 500;
}

.user-detail-list code {
  background: var(--bg);
  padding: 2px 6px;
  border-radius: var(--r3);
  color: var(--brand);
}

.confirm-box {
  padding: 6px 0;
}

.confirm-prompt {
  font-size: var(--text-body);
  color: var(--t1);
  line-height: 1.6;
  margin: 0;
}

.highlight-user {
  color: var(--t1);
}

.highlight-role {
  color: var(--brand);
}

.confirm-note {
  margin: 8px 0 0;
  font-size: var(--text-muted);
  color: var(--t3);
}
.custom-pagination {
  flex-wrap: wrap;
  row-gap: 8px;
  white-space: normal;
}
</style>
