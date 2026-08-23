<template>
  <div v-loading="loading">
    <div class="page-header">
      <div class="back-title">
        <el-button :icon="ArrowLeft" circle @click="goBack" />
        <h2>{{ knowledge?.name || '知识库' }}</h2>
        <el-tag :type="knowledge?.isDelete === 1 ? 'danger' : 'success'" size="small" effect="plain">
          {{ knowledge?.isDelete === 1 ? '已删除' : '正常' }}
        </el-tag>
        <span class="owner">所属用户 ID：{{ knowledge?.userId }}</span>
        <span class="owner" v-if="knowledge">文档数：{{ knowledge.docCount }}</span>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" @click="loadDocs(1)">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索文档名" clearable style="width: 240px" :prefix-icon="Search" @keyup.enter="loadDocs(1)" @clear="loadDocs(1)" />
        <el-select v-model="deletedFilter" placeholder="文档状态" style="width: 140px" @change="loadDocs(1)">
          <el-option label="全部文档" :value="null" />
          <el-option label="正常文档" :value="0" />
          <el-option label="已删除" :value="1" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadDocs(1)">查询</el-button>
      </div>

      <el-table :data="docs" stripe>
        <el-table-column label="文件名" min-width="220">
          <template #default="{ row }">
            <el-icon class="file-icon"><Document /></el-icon>
            <span>{{ row.name }}</span>
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
        <el-table-column label="向量化状态" width="110">
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
        <el-table-column prop="createTime" label="上传时间" width="170">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <template v-if="row.isDelete === 1">
              <el-button size="small" type="success" :icon="RefreshLeft" @click="handleRestore(row)">恢复</el-button>
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
              <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
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
        @current-change="loadDocs()"
        @size-change="loadDocs(1)"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Document, Refresh, RefreshLeft, RefreshRight, Search } from '@element-plus/icons-vue'
import { adminGetKnowledge, adminDeleteDoc, adminRemoveDocVector, listAllDocs, restoreDoc, reVectorizeDoc } from '../api/admin'
import { getTask } from '../api/knowledge'

const route = useRoute()
const router = useRouter()
// 雪花 ID 超出 JS Number 安全整数范围，必须用字符串透传（后端已序列化为字符串）
const knowledgeId = route.params.id

const knowledge = ref(null)
const docs = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const keyword = ref('')
const deletedFilter = ref(0) // 默认只看正常文档；null=全部, 0=正常, 1=已删除
const busyIds = ref([]) // 正在重新入库的文档 id，禁用按钮防重复提交

// 追踪进行中的轮询定时器，组件卸载时统一清理，防止 setInterval 泄漏
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
        // 任务过期或查询失败则停止轮询，依赖文档列表刷新兜底
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

const handleRestore = async (row) => {
  await ElMessageBox.confirm(`确定恢复文档「${row.name}」吗？将重新向量化入库。`, '恢复确认', { type: 'info' })
  await runVectorize(row, (r) => restoreDoc(knowledgeId, r.id))
}

const handleDelete = async (row) => {
  await ElMessageBox.confirm(`确定删除文档「${row.name}」吗？将同时清除其向量，之后可恢复。`, '删除确认', { type: 'warning' })
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
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.back-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.back-title h2 {
  font-size: 20px;
}
.owner {
  font-size: 13px;
  color: #909399;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.file-icon {
  margin-right: 6px;
  color: #409eff;
  vertical-align: middle;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
