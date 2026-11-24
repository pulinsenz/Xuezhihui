<template>
  <div class="admin-page">
    <div class="page-header">
      <div>
        <p class="page-kicker">系统管控</p>
        <h2>全局知识库</h2>
              </div>
    </div>

    <section class="admin-panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索知识库名称"
          clearable
          style="width: 280px"
          :prefix-icon="Search"
          @keyup.enter="loadData(1)"
          @clear="loadData(1)"
        />
        <el-select v-model="deletedFilter" placeholder="知识库状态" style="width: 150px" @change="loadData(1)">
          <el-option label="全部知识库" :value="null" />
          <el-option label="正常知识库" :value="0" />
          <el-option label="已删除" :value="1" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData(1)">查询</el-button>
      </div>

      <div class="table-card">
        <el-table :data="list" stripe v-loading="loading">
          <el-table-column prop="name" label="知识库名称" min-width="180">
            <template #default="{ row }">
              <span class="kb-name-text">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="userId" label="所属用户 ID" width="180" />
          <el-table-column prop="ownerNickname" label="所属昵称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="docCount" label="文档数" width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.docCount }} 篇</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.isDelete === 1 ? 'danger' : 'success'" size="small" effect="plain">
                {{ row.isDelete === 1 ? '已删除' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="190">
            <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" :icon="FolderOpened" @click="openDetail(row)">查看文档</el-button>
              <template v-if="row.isDelete === 1">
                <el-button size="small" type="success" plain :icon="RefreshLeft" @click="handleRestore(row)">恢复</el-button>
              </template>
              <template v-else>
                <el-button size="small" type="danger" plain :icon="Delete" @click="handleDelete(row)">删除</el-button>
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
        @current-change="loadData()"
        @size-change="loadData(1)"
      />
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, FolderOpened, RefreshLeft, Search } from '@element-plus/icons-vue'
import { listAllKnowledge, adminDeleteKnowledge, restoreKnowledge } from '../api/admin'

const router = useRouter()

const keyword = ref('')
const deletedFilter = ref(0)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const loadData = async (page) => {
  if (page) pageNum.value = page
  loading.value = true
  try {
    const data = await listAllKnowledge({
      keyword: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      deleted: deletedFilter.value,
    })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const openDetail = (row) => {
  router.push(`/admin/knowledge/${row.id}`)
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(
    `确定删除知识库「${row.name}」吗？其下 ${row.docCount} 个文档将一并删除并清除向量，之后可恢复。`,
    '删除确认',
    { type: 'warning' }
  )
  await adminDeleteKnowledge(row.id)
  ElMessage.success('已删除')
  loadData()
}

const handleRestore = async (row) => {
  await ElMessageBox.confirm(
    `确定恢复知识库「${row.name}」吗？其下 ${row.docCount} 个文档将重新向量化入库。`,
    '恢复确认',
    { type: 'info' }
  )
  const taskIds = (await restoreKnowledge(row.id)) || []
  ElMessage.success(taskIds.length ? `已恢复，正在重新入库 ${taskIds.length} 个文档` : '已恢复')
  loadData()
}

onMounted(loadData)
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
  font-size: 12px;
  color: var(--brand);
  font-weight: 600;
}

.page-header h2 {
  margin: 4px 0 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--t1);
}

.admin-panel {
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 16px;
  box-shadow: var(--shadow-card);
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

.table-card :deep(.el-button--small) {
  min-height: 28px;
}

.kb-name-text {
  font-size: var(--text-heading);
  font-weight: 600;
  color: var(--t1);
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>