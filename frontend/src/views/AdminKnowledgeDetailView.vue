<template>
  <div class="admin-page" v-loading="loading">
    <div class="page-header">
      <div class="back-title">
        <el-button :icon="ArrowLeft" circle @click="goBack" class="back-btn" />
        <div>
          <div class="title-status-row">
            <h2>{{ knowledge?.name || '知识库详情' }}</h2>
            <el-tag :type="knowledge?.isDelete === 1 ? 'danger' : 'success'" size="small" effect="plain">
              {{ knowledge?.isDelete === 1 ? '已删除' : '正常' }}
            </el-tag>
          </div>
          <div class="meta-row">
            <span>所属用户 ID: {{ knowledge?.userId }}</span>
            <span v-if="knowledge">· 共 {{ knowledge.docCount }} 个文档</span>
          </div>
        </div>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="loadDocs(1)">刷新数据</el-button>
      </div>
    </div>

    <section class="admin-panel">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索文档名"
          clearable
          style="width: 260px"
          :prefix-icon="Search"
          @keyup.enter="loadDocs(1)"
          @clear="loadDocs(1)"
        />
        <el-select v-model="deletedFilter" placeholder="文档状态" style="width: 140px" @change="loadDocs(1)">
          <el-option label="全部文档" :value="null" />
          <el-option label="正常文档" :value="0" />
          <el-option label="已删除" :value="1" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadDocs(1)">查询</el-button>
      </div>

      <div class="batch-toolbar" v-if="selectedRows.length">
        <span class="batch-hint">已选择 {{ selectedRows.length }} 项</span>
        <el-button size="small" :icon="RefreshLeft" @click="handleBatchRemoveVector">
          批量移除入库
        </el-button>
        <el-button size="small" type="danger" plain :icon="Delete" @click="handleBatchDelete">
          批量删除
        </el-button>
      </div>

      <div class="table-card">
        <el-table :data="docs" stripe @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="45" />
          <el-table-column label="文件名" min-width="240">
            <template #default="{ row }">
              <div class="file-name-cell">
                <el-icon class="file-icon"><Document /></el-icon>
                <span class="file-name-text">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="fileType" label="类型" width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.fileType || '未知' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="100">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="向量化状态" width="120">
            <template #default="{ row }">
              <el-tooltip v-if="row.vectorStatus === 'FAILED'" :content="row.errorMsg || '向量化失败'" placement="top">
                <el-tag type="danger" size="small" effect="plain">失败</el-tag>
              </el-tooltip>
              <el-tag v-else-if="row.vectorStatus === 'SUCCESS'" type="success" size="small" effect="plain">已入库</el-tag>
              <el-tooltip v-else-if="row.vectorStatus === 'SKIPPED'" :content="row.errorMsg || '重复文件'" placement="top">
                <el-tag type="info" size="small" effect="plain">重复未入库</el-tag>
              </el-tooltip>
              <el-tag v-else-if="row.vectorStatus === 'REMOVED'" type="warning" size="small" effect="plain">未入库</el-tag>
              <el-tag v-else type="warning" size="small" effect="plain">待处理</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.isDelete === 1 ? 'danger' : 'success'" size="small" effect="plain">
                {{ row.isDelete === 1 ? '已删除' : '正常' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="上传时间" width="180">
            <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <template v-if="row.isDelete === 1">
                <el-button size="small" type="success" plain :icon="RefreshLeft" @click="handleRestore(row)">恢复</el-button>
              </template>
              <template v-else>
                <el-button
                  v-if="row.vectorStatus === 'SUCCESS'"
                  size="small"
                  type="warning"
                  plain
                  @click="handleRemoveVector(row)"
                >
                  移除入库
                </el-button>
                <el-button
                  v-if="row.vectorStatus === 'FAILED' || row.vectorStatus === 'PENDING' || row.vectorStatus === 'REMOVED' || row.vectorStatus === 'SKIPPED'"
                  size="small"
                  :icon="RefreshRight"
                  :loading="busyIds.includes(row.id)"
                  @click="handleReVectorize(row)"
                >
                  {{ row.vectorStatus === 'SKIPPED' ? '强制入库' : '重新入库' }}
                </el-button>
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
        @current-change="loadDocs()"
        @size-change="loadDocs(1)"
      />
    </section>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Document, Refresh, RefreshLeft, RefreshRight, Search } from '@element-plus/icons-vue'
import { adminBatchDeleteDocs, adminBatchRemoveVector, adminGetKnowledge, adminDeleteDoc, adminRemoveDocVector, listAllDocs, restoreDoc, reVectorizeDoc } from '../api/admin'
import { getTask } from '../api/knowledge'

const route = useRoute()
const router = useRouter()
const knowledgeId = route.params.id

const knowledge = ref(null)
const docs = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const keyword = ref('')
const deletedFilter = ref(0)
const busyIds = ref([])
const selectedRows = ref([])

const pollTimers = new Set()

const loadDocs = async (page) => {
  if (page) pageNum.value = page
  loading.value = true
  try {
    const data = await listAllDocs(knowledgeId, {
      keyword: keyword.value.trim() || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      deleted: deletedFilter.value,
    })
    docs.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const loadKnowledge = async () => {
  knowledge.value = await adminGetKnowledge(knowledgeId)
}

const pollTask = (taskId) =>
  new Promise((resolve) => {
    const timer = setInterval(async () => {
      try {
        const task = await getTask(taskId)
        if (task?.status === 'SUCCESS' || task?.status === 'FAILED') {
          clearInterval(timer)
          pollTimers.delete(timer)
          resolve(task)
        }
      } catch (err) {
        clearInterval(timer)
        pollTimers.delete(timer)
        resolve(null)
      }
    }, 1000)
    pollTimers.add(timer)
  })

const runVectorize = async (row, actionText) => {
  busyIds.value = [...busyIds.value, row.id]
  try {
    const taskId = await actionText(row)
    ElMessage.success('已提交，正在向量化...')
    const task = await pollTask(taskId)
    await loadDocs()
    if (task?.status === 'FAILED') {
      ElMessage.warning('向量化失败：' + (task.message || '未知原因'))
    } else if (task?.status === 'SUCCESS') {
      ElMessage.success('向量化完成，已可检索')
    }
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    busyIds.value = busyIds.value.filter((id) => id !== row.id)
  }
}

const handleReVectorize = async (row) => {
  await runVectorize(row, (r) => reVectorizeDoc(knowledgeId, r.id))
}

const handleRemoveVector = async (row) => {
  await ElMessageBox.confirm(
    `确定将文档「${row.name}」移出入库吗？将删除其向量，文档记录保留，之后可重新入库。`,
    '移除入库',
    { type: 'warning' }
  )
  await adminRemoveDocVector(knowledgeId, row.id)
  ElMessage.success('已移出入库')
  loadDocs()
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const handleBatchRemoveVector = async () => {
  const ids = selectedRows.value.map((r) => r.id)
  if (!ids.length) return
  await ElMessageBox.confirm(`确定将选中的 ${ids.length} 个文档移出入库吗？将删除其向量，文档记录保留。`, '批量移除入库', {
    type: 'warning',
  })
  const count = await adminBatchRemoveVector(knowledgeId, ids)
  ElMessage.success(`已移除 ${count} 个文档的入库`)
  loadDocs()
}

const handleBatchDelete = async () => {
  const ids = selectedRows.value.map((r) => r.id)
  if (!ids.length) return
  await ElMessageBox.confirm(
    `确定删除选中的 ${ids.length} 个文档吗？将记录各自文件哈希、同步删除所有相同内容文档，并禁止再次上传与恢复。`,
    '批量删除',
    { type: 'warning' }
  )
  const count = await adminBatchDeleteDocs(knowledgeId, ids)
  ElMessage.success(`已删除 ${count} 个文档`)
  loadDocs()
}

const handleRestore = async (row) => {
  await ElMessageBox.confirm(`确定恢复文档「${row.name}」吗？将重新向量化入库。`, '恢复确认', { type: 'info' })
  await runVectorize(row, (r) => restoreDoc(knowledgeId, r.id))
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(
    `确定删除文档「${row.name}」吗？将记录其文件哈希、同步删除所有相同内容文档，并禁止再次上传与恢复。`,
    '删除确认',
    { type: 'warning' }
  )
  await adminDeleteDoc(knowledgeId, row.id)
  ElMessage.success('已删除')
  loadDocs()
}

const formatSize = (bytes) => {
  bytes = Number(bytes)
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

const goBack = () => router.push('/admin/knowledge')

onMounted(() => {
  loadKnowledge()
  loadDocs()
})
onBeforeUnmount(() => {
  pollTimers.forEach(clearInterval)
  pollTimers.clear()
})
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 2px;
}

.back-title {
  display: flex;
  align-items: center;
  gap: 14px;
}

.back-btn {
  border: 1px solid rgba(148, 163, 184, 0.25);
  background: rgba(255, 255, 255, 0.8);
}

.title-status-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-status-row h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.meta-row {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
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
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 16px;
  background: rgba(241, 245, 249, 0.8);
  border-radius: 12px;
}

.batch-hint {
  font-size: 13px;
  font-weight: 500;
  color: #0f766e;
}

.table-card {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon {
  color: #0f766e;
  font-size: 16px;
}

.file-name-text {
  font-weight: 600;
  color: #0f172a;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>