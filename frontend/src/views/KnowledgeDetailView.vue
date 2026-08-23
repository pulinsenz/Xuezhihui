<template>
  <div v-loading="loading">
    <div class="page-header">
      <div class="back-title">
        <el-button :icon="ArrowLeft" circle @click="goBack" />
        <h2>{{ knowledge?.name || '知识库' }}</h2>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="loadDocs">刷新</el-button>
        <el-button type="primary" :icon="Upload" :loading="uploading" @click="uploadRef.click()">
          上传文档
        </el-button>
        <input ref="uploadRef" type="file" class="hidden-input" @change="handleUpload" />
      </div>
    </div>

    <el-alert
      v-if="knowledge?.description"
      :title="knowledge.description"
      type="info"
      :closable="false"
      class="desc-alert"
    />

    <el-empty v-if="!loading && docs.length === 0" description="暂无文档，点击右上角上传" />

    <template v-else>
      <div class="docs-toolbar">
        <el-select v-model="deletedFilter" placeholder="文档状态" style="width: 130px" @change="loadDocs()">
          <el-option label="正常文档" :value="0" />
          <el-option label="已删除" :value="1" />
          <el-option label="全部" :value="null" />
        </el-select>
      </div>
      <div class="batch-toolbar">
        <el-button size="small" :disabled="!selectedRows.length" :icon="RefreshLeft" @click="handleBatchRemoveVector">
          批量移除入库
        </el-button>
        <el-button size="small" type="danger" :disabled="!selectedRows.length" :icon="Delete" @click="handleBatchDelete">
          批量删除
        </el-button>
      </div>

      <el-table :data="docs" stripe class="doc-table" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" />
        <el-table-column label="文件名" min-width="240">
          <template #default="{ row }">
            <el-icon class="file-icon"><Document /></el-icon>
            <span>{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.fileType || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="向量化状态" width="150">
          <template #default="{ row }">
            <el-tooltip v-if="row.vectorStatus === 'FAILED'" :content="row.errorMsg || '向量化失败'" placement="top">
              <el-tag type="danger" size="small">失败</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.vectorStatus === 'SUCCESS'" type="success" size="small">已入库</el-tag>
            <el-tooltip v-else-if="row.vectorStatus === 'SKIPPED'" :content="row.errorMsg || '重复文件'" placement="top">
              <el-tag type="info" size="small">重复未入库</el-tag>
            </el-tooltip>
            <el-tag v-else-if="row.vectorStatus === 'REMOVED'" type="warning" size="small">未入库</el-tag>
            <el-tag v-else type="warning" size="small">待处理</el-tag>
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
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <!-- 已删除：用户删的可恢复，管理员删的锁定 -->
            <template v-if="row.isDelete === 1">
              <el-button
                v-if="row.deleteSource !== 'admin'"
                size="small"
                type="success"
                :loading="busyIds.includes(row.id)"
                @click="handleRestoreDoc(row)"
              >
                恢复
              </el-button>
              <el-tooltip v-else :content="'该文件已被管理员删除，无法恢复或重新上传'" placement="top">
                <el-tag type="info" size="small">已被管理员删除</el-tag>
              </el-tooltip>
            </template>
            <!-- 正常文档 -->
            <template v-else>
              <el-button v-if="row.vectorStatus === 'SUCCESS'" size="small" type="warning" plain @click="handleRemoveVector(row)">
                移除入库
              </el-button>
              <el-button
                v-if="row.vectorStatus === 'SKIPPED'"
                size="small"
                type="primary"
                :loading="busyIds.includes(row.id)"
                @click="handleReVectorize(row)"
              >
                强制入库
              </el-button>
              <el-button
                v-if="row.vectorStatus === 'FAILED' || row.vectorStatus === 'PENDING' || row.vectorStatus === 'REMOVED'"
                size="small"
                type="primary"
                :loading="busyIds.includes(row.id)"
                @click="handleReVectorize(row)"
              >
                重新入库
              </el-button>
              <el-button size="small" type="danger" @click="handleDeleteDoc(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Document, Refresh, RefreshLeft, Upload } from '@element-plus/icons-vue'
import {
  batchDeleteDocs,
  batchRemoveVector,
  deleteDoc,
  getKnowledge,
  getTask,
  listDocs,
  reVectorizeDoc,
  removeDocVector,
  restoreDoc,
  uploadDoc,
} from '../api/knowledge'

const route = useRoute()
const router = useRouter()
// 雪花 ID 超出 JS Number 安全整数范围，必须用字符串透传（后端已序列化为字符串）
const knowledgeId = route.params.id

const knowledge = ref(null)
const docs = ref([])
const loading = ref(false)
const uploading = ref(false)
const busyIds = ref([]) // 正在入库的文档 id，禁用按钮防重复提交
const selectedRows = ref([]) // 勾选的行，用于批量操作
const deletedFilter = ref(0) // 文档状态过滤：0=正常 1=已删除 null=全部
const uploadRef = ref(null)
let pollTimer = null

const loadDocs = async () => {
  loading.value = true
  try {
    docs.value = await listDocs(knowledgeId, deletedFilter.value)
  } finally {
    loading.value = false
  }
}

const loadKnowledge = async () => {
  knowledge.value = await getKnowledge(knowledgeId)
}

const handleUpload = async (e) => {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    // 上传返回 taskId：向量化走 Redis 消息队列（Python worker 异步执行），前端轮询任务状态
    const taskId = await uploadDoc(knowledgeId, file)
    if (!taskId) {
      // 重复文件：已创建记录但默认未入库，提醒用户可强制入库
      ElMessage.warning('该文件与已有文档内容相同，默认未入库；可点击「强制入库」')
      await loadDocs()
      return
    }
    ElMessage.success('上传成功，正在向量化...')
    clearInterval(pollTimer)
    pollTimer = setInterval(async () => {
      try {
        const task = await getTask(taskId)
        const done = task?.status === 'SUCCESS' || task?.status === 'FAILED'
        if (done) {
          clearInterval(pollTimer)
          await loadDocs()
          if (task.status === 'FAILED') {
            ElMessage.warning('向量化失败：' + (task.message || '未知原因'))
          } else {
            ElMessage.success('向量化完成，已可检索')
          }
        }
      } catch (err) {
        // 任务状态查询失败（任务过期等）则停止轮询，依赖文档列表刷新兜底
        clearInterval(pollTimer)
        await loadDocs()
      }
    }, 1000)
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    uploading.value = false
  }
}

// 强制入库（重复文件）/ 重新入库（失败、待处理）：重新提交向量化任务并轮询
const handleReVectorize = async (row) => {
  busyIds.value = [...busyIds.value, row.id]
  try {
    const taskId = await reVectorizeDoc(knowledgeId, row.id)
    ElMessage.success('已提交，正在向量化...')
    clearInterval(pollTimer)
    pollTimer = setInterval(async () => {
      try {
        const task = await getTask(taskId)
        const done = task?.status === 'SUCCESS' || task?.status === 'FAILED'
        if (done) {
          clearInterval(pollTimer)
          await loadDocs()
          if (task.status === 'FAILED') {
            ElMessage.warning('向量化失败：' + (task.message || '未知原因'))
          } else {
            ElMessage.success('向量化完成，已可检索')
          }
        }
      } catch (err) {
        // 任务过期或查询失败则停止轮询，依赖文档列表刷新兜底
        clearInterval(pollTimer)
        await loadDocs()
      }
    }, 1000)
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    busyIds.value = busyIds.value.filter((id) => id !== row.id)
  }
}

// 移除入库：删除文档向量（保留文档记录），状态置为未入库，可重新入库
const handleRemoveVector = async (row) => {
  await ElMessageBox.confirm(
    `确定将文档「${row.name}」移出入库吗？将删除其向量，文档记录保留，之后可重新入库。`,
    '移除入库',
    { type: 'warning' }
  )
  await removeDocVector(knowledgeId, row.id)
  ElMessage.success('已移出入库')
  loadDocs()
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

// 删除单个文档（同时删向量）
const handleDeleteDoc = async (row) => {
  await ElMessageBox.confirm(
    `确定删除文档「${row.name}」吗？将同时删除其向量，之后不可恢复。`,
    '删除确认',
    { type: 'warning' }
  )
  await deleteDoc(knowledgeId, row.id)
  ElMessage.success('已删除')
  loadDocs()
}

// 批量移除入库：勾选的已入库文档删除向量、保留记录
const handleBatchRemoveVector = async () => {
  const ids = selectedRows.value.map((r) => r.id)
  if (!ids.length) return
  await ElMessageBox.confirm(`确定将选中的 ${ids.length} 个文档移出入库吗？将删除其向量，文档记录保留。`, '批量移除入库', {
    type: 'warning',
  })
  const count = await batchRemoveVector(knowledgeId, ids)
  ElMessage.success(`已移除 ${count} 个文档的入库`)
  loadDocs()
}

// 批量删除文档（同时删各文档向量）
const handleBatchDelete = async () => {
  const ids = selectedRows.value.map((r) => r.id)
  if (!ids.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 个文档吗？将同时删除其向量，之后不可恢复。`, '批量删除', {
    type: 'warning',
  })
  const count = await batchDeleteDocs(knowledgeId, ids)
  ElMessage.success(`已删除 ${count} 个文档`)
  loadDocs()
}

// 恢复用户自己删除的文档（管理员删除的已锁定不可恢复），恢复后重新入库
const handleRestoreDoc = async (row) => {
  await ElMessageBox.confirm(`确定恢复文档「${row.name}」吗？将重新向量化入库。`, '恢复确认', { type: 'info' })
  busyIds.value = [...busyIds.value, row.id]
  try {
    const taskId = await restoreDoc(knowledgeId, row.id)
    ElMessage.success('已恢复，正在重新入库...')
    clearInterval(pollTimer)
    pollTimer = setInterval(async () => {
      try {
        const task = await getTask(taskId)
        const done = task?.status === 'SUCCESS' || task?.status === 'FAILED'
        if (done) {
          clearInterval(pollTimer)
          await loadDocs()
          if (task.status === 'FAILED') {
            ElMessage.warning('向量化失败：' + (task.message || '未知原因'))
          } else {
            ElMessage.success('恢复完成，已可检索')
          }
        }
      } catch (err) {
        // 任务过期或查询失败则停止轮询，依赖文档列表刷新兜底
        clearInterval(pollTimer)
        await loadDocs()
      }
    }, 1000)
  } catch (err) {
    // 错误已由拦截器提示
  } finally {
    busyIds.value = busyIds.value.filter((id) => id !== row.id)
  }
}

const formatSize = (bytes) => {
  bytes = Number(bytes)
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

const goBack = () => router.push('/knowledge')

onMounted(() => {
  loadKnowledge()
  loadDocs()
})
onBeforeUnmount(() => clearInterval(pollTimer))
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.back-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.back-title h2 {
  font-size: 20px;
}
.desc-alert {
  margin-bottom: 16px;
}
.hidden-input {
  display: none;
}
.file-icon {
  margin-right: 6px;
  color: #409eff;
  vertical-align: middle;
}
.doc-table {
  margin-top: 4px;
}
.batch-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.docs-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
</style>
