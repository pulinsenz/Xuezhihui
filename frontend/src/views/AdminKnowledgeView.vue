<template>
  <div class="admin-page">
    <div class="page-head">
      <div class="head-left">
        <h2 class="page-title">全站知识库管控</h2>
      </div>
      <div class="head-stat-pill">
        <span class="stat-label">全局知识库总数</span>
        <strong class="stat-value">{{ total }}</strong>
      </div>
    </div>

    <section class="admin-card">
      <div class="toolbar">
        <div class="toolbar-search-group">
          <el-input
            v-model="keyword"
            placeholder="搜索知识库名称 / 关键词..."
            clearable
            class="search-input"
            aria-label="搜索知识库"
            :prefix-icon="Search"
            @keyup.enter="loadData(1)"
            @clear="loadData(1)"
          />
          <el-select
            v-model="deletedFilter"
            placeholder="状态过滤"
            class="filter-select"
            @change="loadData(1)"
          >
            <el-option label="全部状态" :value="null" />
            <el-option label="正常在用" :value="0" />
            <el-option label="已删除" :value="1" />
          </el-select>
        </div>
        <div class="toolbar-actions">
          <el-button type="primary" :icon="Search" class="btn-brand" @click="loadData(1)">查询数据</el-button>
          <el-button :icon="Refresh" @click="loadData(1)">刷新</el-button>
        </div>
      </div>

      <div class="table-shell">
        <el-table :data="list" stripe v-loading="loading" class="custom-admin-table">
          <el-table-column prop="name" label="知识库名称" min-width="220">
            <template #default="{ row }">
              <div class="kb-name-cell">
                <div class="kb-icon-letter">
                  {{ (row.name || '知').charAt(0) }}
                </div>
                <span class="kb-name-text" :title="row.name">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="userId" label="所属用户 ID" width="140">
            <template #default="{ row }">
              <code class="id-badge">#{{ row.userId }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="ownerNickname" label="所属作者昵称" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="author-name">{{ row.ownerNickname || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="docCount" label="文档规模" width="120">
            <template #default="{ row }">
              <span class="doc-badge">{{ row.docCount || 0 }} 篇文档</span>
            </template>
          </el-table-column>
          <el-table-column label="运行状态" width="110">
            <template #default="{ row }">
              <span class="status-badge" :class="row.isDelete === 1 ? 'is-deleted' : 'is-normal'">
                <span class="status-dot" />
                {{ row.isDelete === 1 ? '已删除' : '正常' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="180">
            <template #default="{ row }">
              <span class="time-text">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="管理操作" width="190">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button size="small" plain :icon="FolderOpened" @click="openDetail(row)">文档明细</el-button>
                <template v-if="row.isDelete === 1">
                  <el-button size="small" type="success" plain :icon="RefreshLeft" @click="handleRestore(row)">恢复</el-button>
                </template>
                <template v-else>
                  <el-button size="small" type="danger" plain :icon="Delete" @click="handleDelete(row)">删除</el-button>
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
          @current-change="loadData()"
          @size-change="loadData(1)"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, FolderOpened, Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
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

.kb-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kb-icon-letter {
  width: 28px;
  height: 28px;
  border-radius: var(--r3);
  background: var(--tint);
  color: var(--brand);
  border: 1px solid var(--tint-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-body);
  font-weight: 700;
  flex-shrink: 0;
}

.kb-name-text {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.id-badge {
  font-family: monospace;
  font-size: var(--text-muted);
  background: var(--bg);
  padding: 2px 6px;
  border-radius: var(--r3);
  color: var(--t3);
}

.author-name {
  font-size: var(--text-body);
  color: var(--t2);
}

.doc-badge {
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--brand);
  background: var(--tint);
  border: 1px solid var(--tint-strong);
  padding: 2px 8px;
  border-radius: var(--r-pill);
  display: inline-block;
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
