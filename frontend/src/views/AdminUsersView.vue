<template>
  <div class="admin-page">
    <div class="page-header">
      <div>
        <p class="page-kicker">系统管控</p>
        <h2>用户管理</h2>
        <p class="page-subtitle">查看全量注册用户、分配管理员权限与维护账户生命周期</p>
      </div>
    </div>

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
              <el-tag :type="row.userRole === 'admin' ? 'warning' : 'info'" size="small" effect="plain">
                {{ row.userRole === 'admin' ? '管理员' : '普通用户' }}
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
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <template v-if="row.isDelete === 1">
                <el-button v-if="row.id !== authStore.user?.id" size="small" type="success" plain :icon="RefreshLeft" @click="handleRestore(row)">
                  恢复
                </el-button>
                <el-tag v-else size="small" type="info">当前账号</el-tag>
              </template>
              <template v-else>
                <el-button v-if="row.id !== authStore.user?.id" size="small" :icon="Edit" @click="openRoleDialog(row)">
                  {{ row.userRole === 'admin' ? '设为用户' : '设为管理员' }}
                </el-button>
                <el-button v-if="row.id !== authStore.user?.id" size="small" type="danger" plain :icon="Delete" @click="handleDelete(row)">
                  删除
                </el-button>
                <el-tag v-else size="small" type="info">当前账号</el-tag>
              </template>
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

    <el-dialog v-model="roleDialogVisible" :title="roleDialogTitle" width="420px" class="custom-dialog">
      <p style="color: #475569; line-height: 1.6; margin: 12px 0;">
        确定将用户「<strong style="color: #0f172a;">{{ roleTarget?.userAccount }}</strong>」设为
        <strong style="color: #0f766e;">{{ roleTarget?.userRole === 'admin' ? '普通用户' : '管理员' }}</strong>吗？
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
import { Delete, Edit, RefreshLeft, Search } from '@element-plus/icons-vue'
import { listUsers, updateUserRole, deleteUser, restoreUser } from '../api/admin'
import { useAuthStore } from '../stores/auth'

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

const openRoleDialog = (row) => {
  roleTarget.value = row
  roleDialogTitle.value = `调整用户角色 - ${row.userAccount}`
  roleDialogVisible.value = true
}

const confirmRole = async () => {
  const targetRole = roleTarget.value.userRole === 'admin' ? 'user' : 'admin'
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

onMounted(loadUsers)
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-header {
  padding: 4px 2px;
}

.page-kicker {
  font-size: 13px;
  color: #0f766e;
  font-weight: 600;
}

.page-header h2 {
  margin: 4px 0 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.page-subtitle {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.admin-panel {
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 24px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(16px);
  padding: 24px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.table-card {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.user-account-text {
  font-weight: 600;
  color: #0f172a;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>