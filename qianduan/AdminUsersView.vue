<template>
  <div class="admin-page">
    <section class="admin-panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索账号 / 昵称"
          clearable
          style="width: 280px"
          :prefix-icon="Search"
          @keyup.enter="loadUsers(1)"
          @clear="loadUsers(1)"
        />
        <el-select v-model="deletedFilter" placeholder="用户状态" style="width: 150px" @change="loadUsers(1)">
          <el-option label="全部用户" :value="null" />
          <el-option label="正常用户" :value="0" />
          <el-option label="已删除" :value="1" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadUsers(1)">查询</el-button>
      </div>

      <div class="table-card">
        <el-table :data="users" stripe v-loading="loading">
          <el-table-column prop="userAccount" label="账号" min-width="150">
            <template #default="{ row }">
              <span class="user-account-text">{{ row.userAccount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="userName" label="昵称" min-width="150" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">
              <el-tag :type="row.userRole === ROLE.SUPER_ADMIN ? 'primary' : row.userRole === ROLE.ADMIN ? 'warning' : 'info'" size="small" effect="plain">
                {{ roleLabel(row.userRole) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isDelete === 1 ? 'danger' : 'success'" size="small" effect="plain">
                {{ row.isDelete === 1 ? '已删除' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="注册时间" width="190">
            <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="154" align="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button size="small" :disabled="isSelf(row)" @click="userDetail = row">
                  {{ isSelf(row) ? '本账号' : '管理' }}
                </el-button>
                <el-dropdown v-if="hasMoreActions(row)" trigger="click" @command="handleRowCommand($event, row)">
                  <el-button size="small" :aria-label="`${row.userAccount} 的更多操作`">
                    更多<el-icon class="more-icon"><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="canChangeRole(row)" command="role" :icon="Edit">
                        设为{{ roleLabel(nextRole(row)) }}
                      </el-dropdown-item>
                      <el-dropdown-item v-if="canDelete(row)" command="delete" :icon="Delete" class="danger-action">
                        删除账号
                      </el-dropdown-item>
                      <el-dropdown-item v-if="canRestore(row)" command="restore" :icon="RefreshLeft">
                        恢复账号
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button v-else size="small" disabled aria-label="无更多操作">—</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="loadUsers()"
        @size-change="loadUsers(1)"
      />
    </section>

    <el-dialog :model-value="Boolean(userDetail)" title="账号信息" width="min(420px, calc(100% - 32px))" @close="userDetail = null">
      <dl v-if="userDetail" class="user-detail">
        <dt>账号</dt><dd>{{ userDetail.userAccount }}</dd>
        <dt>昵称</dt><dd>{{ userDetail.userName || '—' }}</dd>
        <dt>角色</dt><dd>{{ roleLabel(userDetail.userRole) }}</dd>
        <dt>状态</dt><dd>{{ userDetail.isDelete === 1 ? '已删除' : '正常' }}</dd>
      </dl>
    </el-dialog>

    <el-dialog v-model="roleDialogVisible" :title="roleDialogTitle" width="min(420px, calc(100% - 32px))" class="custom-dialog">
      <p style="color: var(--t2); line-height: 1.6; margin: 12px 0;">
        确定将用户「<strong style="color: var(--t1);">{{ roleTarget?.userAccount }}</strong>」设为
        <strong style="color: var(--brand);">{{ roleLabel(nextRole(roleTarget)) }}</strong>吗？
      </p>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleLoading" @click="confirmRole">确定修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, MoreFilled, RefreshLeft, Search } from '@element-plus/icons-vue'
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
.admin-panel {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
  padding: 16px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.table-card {
  border-radius: var(--r2);
  overflow: hidden;
  border: 1px solid var(--line);
}

.row-actions {
  display: grid;
  grid-template-columns: repeat(2, 60px);
  justify-content: end;
  gap: 6px;
}

.row-actions .el-button {
  width: 100%;
  height: 30px;
  margin: 0;
  padding: 0;
  font-size: var(--text-muted);
}

.more-icon { margin-left: 3px; }
.danger-action { color: var(--el-color-danger); }

.user-detail {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 8px 16px;
  overflow-wrap: anywhere;
}

.user-detail dt { color: var(--t3); }
.user-detail dd { margin: 0; color: var(--t1); }

.user-account-text {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
}

.pagination {
  margin-top: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
</style>