<template>
  <div>
    <div class="page-header">
      <div>
        <h2>用户管理</h2>
        <p class="sub">查看全部用户、调整角色、删除违规账号</p>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索账号 / 昵称" clearable style="width: 260px" :prefix-icon="Search" @keyup.enter="loadUsers(1)" @clear="loadUsers(1)" />
        <el-select v-model="deletedFilter" placeholder="用户状态" style="width: 140px" @change="loadUsers(1)">
          <el-option label="全部用户" :value="null" />
          <el-option label="正常用户" :value="0" />
          <el-option label="已删除" :value="1" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadUsers(1)">查询</el-button>
      </div>

      <el-table :data="users" stripe v-loading="loading">
        <el-table-column prop="userAccount" label="账号" min-width="140" />
        <el-table-column prop="userName" label="昵称" min-width="140" />
        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag :type="row.userRole === 'admin' ? 'warning' : 'info'" size="small">
              {{ row.userRole === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isDelete === 1 ? 'danger' : 'success'" size="small" effect="plain">
              {{ row.isDelete === 1 ? '已删除' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="180">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <!-- 已删除用户：仅提供恢复 -->
            <template v-if="row.isDelete === 1">
              <el-button v-if="row.id !== authStore.user?.id" size="small" type="success" :icon="RefreshLeft" @click="handleRestore(row)">
                恢复
              </el-button>
              <el-tag v-else size="small" type="info">当前账号</el-tag>
            </template>
            <!-- 正常用户：改角色 / 删除 -->
            <template v-else>
              <el-button v-if="row.id !== authStore.user?.id" size="small" :icon="Edit" @click="openRoleDialog(row)">
                {{ row.userRole === 'admin' ? '设为普通用户' : '设为管理员' }}
              </el-button>
              <el-button v-if="row.id !== authStore.user?.id" size="small" type="danger" :icon="Delete" @click="handleDelete(row)">
                删除
              </el-button>
              <el-tag v-else size="small" type="info">当前账号</el-tag>
            </template>
          </template>
        </el-table-column>
      </el-table>

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
    </el-card>

    <!-- 改角色确认 -->
    <el-dialog v-model="roleDialogVisible" :title="roleDialogTitle" width="380px">
      <p>确定将用户「{{ roleTarget?.userAccount }}」设为{{ roleTarget?.userRole === 'admin' ? '普通用户' : '管理员' }}吗？</p>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleLoading" @click="confirmRole">确定</el-button>
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
const deletedFilter = ref(0) // 默认只看正常用户；null=全部, 0=正常, 1=已删除
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
  roleDialogTitle.value = `修改角色 - ${row.userAccount}`
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
.page-header {
  margin-bottom: 16px;
}
.page-header h2 {
  font-size: 20px;
}
.sub {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
