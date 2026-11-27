<template>
  <div class="admin-page" v-loading="loading">
    <div class="page-head">
      <div class="back-title-wrap">
        <el-button :icon="ArrowLeft" circle class="back-btn" aria-label="返回知识库列表" @click="goBack" />
        <div class="head-text-block">
          <div class="title-status-row">
            <h2>{{ knowledge?.name || '知识库文档监管' }}</h2>
            <el-tag :type="knowledge?.isDelete === 1 ? 'danger' : 'success'" size="small" effect="plain" class="status-pill">
              {{ knowledge?.isDelete === 1 ? '已标记删除' : '正常运作中' }}
            </el-tag>
          </div>
          <div class="meta-row">
            <span class="meta-item">所属作者 ID: <code>#{{ knowledge?.userId }}</code></span>
            <span class="dot-sep">·</span>
            <span class="meta-item">现有文档总数: <strong>{{ knowledge?.docCount || docs.length }} 篇</strong></span>
          </div>
        </div>
      </div>
      <div class="head-actions">
        <el-button :icon="Refresh" @click="loadDocs(1)">刷新数据</el-button>
      </div>
    </div>

    <section class="admin-card">
      <div class="toolbar">
        <div class="toolbar-search-group">
          <el-input
            v-model="keyword"
            placeholder="搜索文档名称..."
            clearable
            class="search-input"
            aria-label="搜索文档"
            :prefix-icon="Search"
            @keyup.enter="loadDocs(1)"
            @clear="loadDocs(1)"
          />
          <el-select
            v-model="deletedFilter"
            placeholder="文档状态"
            class="filter-select"
            @change="loadDocs(1)"
          >
            <el-option label="全部文档" :value="null" />
            <el-option label="正常在库" :value="0" />
            <el-option label="已删除" :value="1" />
          </el-select>
          <el-button type="primary" :icon="Search" class="btn-brand" @click="loadDocs(1)">查询文档</el-button>
        </div>

        <div v-if="selectedRows.length" class="batch-toolbar">
          <span class="batch-hint">已选中 {{ selectedRows.length }} 项</span>
          <el-button size="small" :icon="RefreshLeft" @click="handleBatchRemoveVector">
            批量移除向量入库
          </el-button>
          <el-button size="small" type="danger" plain :icon="Delete" @click="handleBatchDelete">
            批量物理清除
          </el-button>
        </div>
      </div>

      <div class="table-shell">
        <el-table :data="docs" stripe class="custom-admin-table" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="46" />
          <el-table-column label="文档名称" min-width="240">
            <template #default="{ row }">
              <div class="file-name-cell">
                <div class="file-icon-box">
                  <el-icon><Document /></el-icon>
                </div>
                <el-tooltip :content="row.name" placement="top" :show-after="300">
                  <span class="file-name-text">{{ row.name }}</span>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="fileType" label="格式类型" width="95">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.fileType || '未知' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="存储大小" width="105">
            <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
          </el-table-column>
          <el-table-column label="向量化状态" width="130">
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
          <el-table-column label="可用状态" width="100">
            <template #default="{ row }">
              <span class="status-badge" :class="row.isDelete === 1 ? 'is-deleted' : 'is-normal'">
                <span class="status-dot" />
                {{ row.isDelete === 1 ? '已删除' : '正常' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="上传时间" width="170">
            <template #default="{ row }">
              <span class="time-text">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <div class="row-actions">
                <template v-if="row.isDelete === 1">
                  <el-button size="small" type="success" plain :icon="RefreshLeft" @click="handleRestore(row)">恢复入库</el-button>
                </template>
                <template v-else>
                  <el-button
                    v-if="row.vectorStatus === 'SUCCESS'"
                    size="small"
                    type="warning"
                    plain
                    @click="handleRemoveVector(row)"
                  >
                    移出
                  </el-button>
                  <el-button
                    v-if="row.vectorStatus === 'FAILED' || row.vectorStatus === 'PENDING' || row.vectorStatus === 'REMOVED' || row.vectorStatus === 'SKIPPED'"
                    size="small"
                    type="primary"
                    :icon="RefreshRight"
                    :loading="busyIds.includes(row.id)"
                    @click="handleReVectorize(row)"
                  >
                    {{ row.vectorStatus === 'SKIPPED' ? '强制入库' : '入库' }}
                  </el-button>
                  <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
                </template>
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
          @current-change="loadDocs()"
          @size-change="loadDocs(1)"
        />
      </div>
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
  gap: clamp(14px, 1.6vw, 20px);
  width: 100%;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.back-title-wrap {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.back-btn {
  border-color: var(--line);
  color: var(--t3);
  margin-top: 2px;
  flex-shrink: 0;
}

.head-text-block {
  min-width: 0;
}

.title-status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.title-status-row h2 {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.status-pill {
  border-radius: var(--r-pill);
  font-weight: 600;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: var(--text-body);
  color: var(--t3);
  flex-wrap: wrap;
}

.meta-item code {
  background: var(--bg);
  padding: 1px 6px;
  border-radius: var(--r3);
  color: var(--brand);
}

.meta-item strong {
  color: var(--t1);
}

.dot-sep {
  opacity: 0.6;
}

.head-actions {
  flex-shrink: 0;
}

.head-actions :deep(.el-button) {
  border-radius: var(--r3);
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
  width: clamp(120px, 12vw, 150px);
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

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  background: var(--tint);
  border: 1px solid var(--tint-strong);
  border-radius: var(--r3);
}

.batch-hint {
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--brand);
}

.table-shell {
  border-radius: var(--r3);
  overflow: hidden;
  border: 1px solid var(--line);
}

.file-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-icon-box {
  width: 28px;
  height: 28px;
  border-radius: var(--r3);
  background: var(--tint);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-body);
  flex-shrink: 0;
}

.file-name-text {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.pagination-bar {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.custom-pagination {
  flex-wrap: wrap;
  row-gap: 8px;
  white-space: normal;
}
</style>
