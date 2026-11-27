<template>
  <div class="wiki-page">
    <div class="header-card">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle class="back-btn" @click="goBack" />
        <div class="head-info">
          <div class="title-row">
            <h2>AI Wiki · {{ knowledge?.name || "知识库" }}</h2>
            <el-tag size="small" type="warning" effect="dark">LLM 生成</el-tag>
            <el-tag v-if="knowledge?.isPublic === 1" size="small" type="success" effect="plain">公开</el-tag>
          </div>
          <div class="meta-row">
            <span class="stat"><el-icon><Document /></el-icon>{{ pages.length }} 个页面</span>
            <span class="meta-tip">由知识库文档经 LLM 大纲规划 + 逐页撰写生成，并已回灌向量库供问答检索</span>
          </div>
        </div>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" :disabled="generating" @click="loadAll">刷新</el-button>
        <template v-if="canManage">
          <el-button
            type="primary"
            :icon="MagicStick"
            :loading="generating"
            @click="handleGenerate"
          >{{ pages.length ? "重新生成" : "生成 Wiki" }}</el-button>
          <el-button
            v-if="pages.length"
            type="danger"
            plain
            :icon="Delete"
            :disabled="generating"
            @click="handleDeleteWiki"
          >删除 Wiki</el-button>
        </template>
      </div>
    </div>

    <div v-loading="loading" class="wiki-main-card">
      <template v-if="pages.length">
        <aside class="page-list">
          <div
            v-for="page in pages"
            :key="page.id"
            class="page-item"
            :class="{ active: String(currentId) === String(page.id) }"
            tabindex="0"
            @click="selectPage(page.id)"
            @keydown.enter.prevent="selectPage(page.id)"
          >
            <span class="page-order">{{ page.sortOrder }}</span>
            <span class="page-title">{{ page.title }}</span>
            <el-tag v-if="page.status === 'FAILED'" size="small" type="danger">失败</el-tag>
          </div>
        </aside>
        <main class="page-content">
          <template v-if="current">
            <div class="content-header">
              <h3>{{ current.title }}</h3>
              <el-button
                v-if="canManage && current.status !== 'FAILED'"
                size="small"
                :icon="Edit"
                @click="openEditDialog"
              >编辑</el-button>
            </div>
            <p v-if="current.summary" class="content-summary">{{ current.summary }}</p>
            <div v-if="sourceNames.length" class="content-sources">
              <el-icon><Collection /></el-icon>
              <span>来源：{{ sourceNames.join("、") }}</span>
            </div>
            <el-alert
              v-if="current.status === 'FAILED'"
              type="warning"
              :closable="false"
              :title="`该页生成失败：${current.errorMsg || '未知原因'}，可尝试重新生成整个 Wiki`"
            />
            <div v-else class="md-body" v-html="rendered" />
          </template>
        </main>
      </template>
      <el-empty v-else-if="!loading" description="尚未生成 Wiki">
        <template v-if="canManage">
          <el-button type="primary" :icon="MagicStick" :loading="generating" @click="handleGenerate">
            基于知识库文档生成
          </el-button>
        </template>
        <p v-else class="empty-tip">知识库作者尚未生成 Wiki</p>
      </el-empty>
    </div>

    <!-- 编辑页面对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑 Wiki 页面" width="720px" :close-on-click-modal="false">
      <el-form label-width="64px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" maxlength="120" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input
            v-model="editForm.content"
            type="textarea"
            :rows="16"
            placeholder="Markdown 正文（保存后立即生效）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Collection, Delete, Edit, MagicStick, Refresh } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  deleteWiki,
  generateWiki,
  getKnowledge,
  getTask,
  getWikiPage,
  listDocs,
  listWikiPages,
  updateWikiPage,
} from '../api/knowledge'

const route = useRoute()
const router = useRouter()
const knowledgeId = route.params.id

const knowledge = ref(null)
const pages = ref([])
const loading = ref(false)
const generating = ref(false)
const currentId = ref(null)
const current = ref(null)
const docNameMap = ref({})

const isOwner = computed(() => knowledge.value?.isOwner === true)
const isMember = computed(() => knowledge.value?.isMember === true)
const canManage = computed(() => isOwner.value || isMember.value)

const editDialogVisible = ref(false)
const editing = ref(false)
const editForm = ref({ title: '', content: '' })

// LLM 内容渲染前必须净化（延续项目 XSS 防护风格），Markdown → 安全 HTML
const rendered = computed(() => {
  if (!current.value?.content) return ''
  return DOMPurify.sanitize(marked.parse(current.value.content))
})

const sourceNames = computed(() => {
  try {
    const ids = JSON.parse(current.value?.sourceDocIds || '[]')
    return ids.map((id) => docNameMap.value[id] || `文档 #${id}`)
  } catch {
    return []
  }
})

const goBack = () => router.push(`/knowledge/${knowledgeId}`)

const loadKnowledge = async () => {
  knowledge.value = await getKnowledge(knowledgeId)
}

const loadPages = async () => {
  pages.value = await listWikiPages(knowledgeId)
  // 默认选中第一页（或保持当前选中）
  const keep = pages.value.find((p) => String(p.id) === String(currentId.value))
  await selectPage((keep || pages.value[0])?.id)
}

const loadDocNames = async () => {
  try {
    const docs = await listDocs(knowledgeId, 0, 'all')
    const map = {}
    docs.forEach((d) => {
      map[String(d.id)] = d.name
    })
    docNameMap.value = map
  } catch {
    // 文档名仅用于来源展示，失败不阻塞页面
  }
}

const loadAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadKnowledge(), loadPages(), loadDocNames()])
  } finally {
    loading.value = false
  }
}

const selectPage = async (pageId) => {
  if (!pageId) {
    current.value = null
    currentId.value = null
    return
  }
  currentId.value = pageId
  current.value = await getWikiPage(knowledgeId, pageId)
}

let pollTimer = null

const startPolling = (taskId) => {
  generating.value = true
  pollTimer = setInterval(async () => {
    try {
      const task = await getTask(taskId)
      if (task.status === 'SUCCESS' || task.status === 'FAILED') {
        clearInterval(pollTimer)
        pollTimer = null
        generating.value = false
        if (task.status === 'SUCCESS') {
          ElMessage.success('Wiki 生成完成')
        } else {
          ElMessage.error(task.message || 'Wiki 生成失败，原有内容已保留')
        }
        await loadPages()
      }
    } catch {
      // 任务过期等瞬时错误：下轮重试
    }
  }, 2000)
}

const handleGenerate = async () => {
  if (pages.value.length) {
    try {
      await ElMessageBox.confirm(
        '将基于知识库当前文档重新生成并替换现有 Wiki，是否继续？',
        '重新生成 Wiki',
        { confirmButtonText: '重新生成', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  try {
    const taskId = await generateWiki(knowledgeId)
    ElMessage.success('生成任务已提交，可在本页等待完成（页面数越多耗时越长）')
    startPolling(taskId)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '提交生成任务失败')
  }
}

const handleDeleteWiki = async () => {
  try {
    await ElMessageBox.confirm(
      '将删除全部 Wiki 页面及其向量数据（不影响原始文档），是否继续？',
      '删除 Wiki',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await deleteWiki(knowledgeId)
  ElMessage.success('Wiki 已删除')
  current.value = null
  currentId.value = null
  pages.value = []
}

const openEditDialog = () => {
  editForm.value = { title: current.value?.title || '', content: current.value?.content || '' }
  editDialogVisible.value = true
}

const handleSaveEdit = async () => {
  editing.value = true
  try {
    await updateWikiPage(knowledgeId, currentId.value, editForm.value)
    ElMessage.success('已保存')
    editDialogVisible.value = false
    await loadPages()
  } finally {
    editing.value = false
  }
}

onMounted(loadAll)
onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.wiki-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 20px 32px;
}

.header-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-row h2 {
  margin: 0;
  font-size: 22px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.meta-tip {
  color: var(--el-text-color-placeholder);
}

.actions {
  display: flex;
  flex-shrink: 0;
}

.wiki-main-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  min-height: 480px;
  display: flex;
}

.page-list {
  width: 260px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  padding: 12px 8px;
  overflow-y: auto;
}

.page-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.page-item:hover {
  background: var(--el-fill-color-light);
}

.page-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.page-order {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.page-item.active .page-order {
  background: var(--el-color-primary);
  color: #fff;
}

.page-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.page-content {
  flex: 1;
  min-width: 0;
  padding: 20px 28px;
  overflow-y: auto;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.content-header h3 {
  margin: 0;
  font-size: 16px;
}

.content-summary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 8px 0;
}

.content-sources {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  padding: 6px 10px;
  margin-bottom: 16px;
}

.md-body {
  line-height: 1.75;
  font-size: 14px;
  word-break: break-word;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  margin: 18px 0 10px;
}

.md-body :deep(pre) {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
}

.md-body :deep(code) {
  background: var(--el-fill-color);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
}

.md-body :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
}

.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid var(--el-border-color-light);
  padding: 6px 12px;
}

.empty-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.page-item:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
