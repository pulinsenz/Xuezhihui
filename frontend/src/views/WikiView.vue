<template>
  <div class="wiki-page">
    <header class="header-card">
      <div class="header-left">
        <el-button v-if="!props.knowledgeId" :icon="ArrowLeft" circle class="back-btn" aria-label="返回知识库" @click="goBack" />
        <div class="head-info">
          <div class="title-row">
            <h2>AI Wiki · {{ knowledge?.name || "知识库" }}</h2>
            <span class="status-pill pill-ai">LLM 深度生成</span>
            <span v-if="knowledge?.isPublic === 1" class="status-pill pill-public">公开</span>
          </div>
          <div class="meta-row">
            <span class="stat"><el-icon><Document /></el-icon>{{ pages.length }} 个章节页面</span>
            <span class="dot-sep">·</span>
            <span class="meta-tip">由多篇知识库源文档经规划与逐页精修生成，已同步回灌向量库</span>
          </div>
        </div>
      </div>
      <div class="actions">
        <el-button :icon="Refresh" :disabled="generating" @click="loadAll">刷新</el-button>
        <template v-if="canManage">
          <el-button
            type="primary"
            class="generate-btn"
            :icon="MagicStick"
            :loading="generating"
            @click="handleGenerate"
          >{{ pages.length ? "重新生成 Wiki" : "生成 AI Wiki" }}</el-button>
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
    </header>

    <!-- 主阅读与大纲区域：弹性双栏，大缩放下高度自适应 -->
    <div v-loading="loading" class="wiki-main-card">
      <template v-if="pages.length">
        <aside class="page-list custom-scroll">
          <div class="outline-head">
            <span>章节目录 ({{ pages.length }})</span>
          </div>
          <button
            v-for="page in pages"
            :key="page.id"
            type="button"
            class="page-item"
            :class="{ active: String(currentId) === String(page.id) }"
            :aria-current="String(currentId) === String(page.id) ? 'true' : undefined"
            @click="selectPage(page.id)"
          >
            <span class="page-order">{{ page.sortOrder }}</span>
            <span class="page-title" :title="page.title">{{ page.title }}</span>
            <el-tag v-if="page.status === 'FAILED'" size="small" type="danger">失败</el-tag>
          </button>
        </aside>

        <section class="page-content custom-scroll" aria-label="Wiki 正文">
          <template v-if="current">
            <div class="content-header">
              <div class="title-wrap">
                <span class="chapter-badge">Chapter {{ current.sortOrder }}</span>
                <h3>{{ current.title }}</h3>
              </div>
              <el-button
                v-if="canManage && current.status !== 'FAILED'"
                size="small"
                :icon="Edit"
                class="edit-btn"
                @click="openEditDialog"
              >编辑本页</el-button>
            </div>

            <p v-if="current.summary" class="content-summary">{{ current.summary }}</p>

            <div v-if="sourceNames.length" class="content-sources">
              <el-icon><Collection /></el-icon>
              <span>知识参考来源：{{ sourceNames.join("、") }}</span>
            </div>

            <el-alert
              v-if="current.status === 'FAILED'"
              type="warning"
              :closable="false"
              :title="`该页生成失败：${current.errorMsg || '未知原因'}，可尝试重新生成整个 Wiki`"
            />
            <div v-else class="md-body" v-html="rendered" />
          </template>
        </section>
      </template>

      <el-empty v-else-if="!loading" description="尚未生成 Wiki 知识大纲" class="wiki-empty">
        <template v-if="canManage">
          <el-button type="primary" class="generate-btn" :icon="MagicStick" :loading="generating" @click="handleGenerate">
            基于知识库文档生成 Wiki
          </el-button>
        </template>
        <p v-else class="empty-tip">知识库作者尚未生成 Wiki，请稍后访问</p>
      </el-empty>
    </div>

    <!-- 编辑页面对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑 Wiki 页面" width="min(760px, calc(100vw - 32px))" :close-on-click-modal="false">
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
        <el-button type="primary" :loading="editing" class="generate-btn" @click="handleSaveEdit">保存更改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Collection, Delete, Document, Edit, MagicStick, Refresh } from '@element-plus/icons-vue'
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
const props = defineProps({ knowledgeId: String })
const knowledgeId = props.knowledgeId ?? route.params.id

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
let disposed = false

const startPolling = (taskId) => {
  generating.value = true
  pollTimer = setInterval(async () => {
    try {
      const task = await getTask(taskId)
      if (disposed) return
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
  if (disposed) return
  try {
    const taskId = await generateWiki(knowledgeId)
    if (disposed) return
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
  disposed = true
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.wiki-page {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: clamp(12px, 1.5vw, 18px);
}

.header-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: clamp(14px, 1.8vw, 20px);
  box-shadow: var(--shadow-card);
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  min-width: 0;
  flex: 1 1 clamp(280px, 35vw, 560px);
}

.back-btn {
  border-color: var(--line);
  color: var(--t3);
  margin-top: 2px;
  flex-shrink: 0;
}

.head-info { min-width: 0; flex: 1; }

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.title-row h2 {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.status-pill {
  font-size: var(--text-muted);
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--r-pill);
}

.pill-ai {
  background: color-mix(in srgb, var(--el-color-warning) 12%, transparent);
  color: var(--el-color-warning-dark-2);
  border: 1px solid color-mix(in srgb, var(--el-color-warning) 30%, transparent);
}

.pill-public {
  background: color-mix(in srgb, var(--el-color-success) 12%, transparent);
  color: var(--el-color-success-dark-2);
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  color: var(--t3);
  font-size: var(--text-muted);
  flex-wrap: wrap;
}

.dot-sep { opacity: 0.6; }
.meta-tip { color: var(--t4); }

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.generate-btn {
  background: var(--brand);
  border-color: var(--brand);
  font-weight: 600;
}

.generate-btn:hover {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
}

/* 核心双栏阅读卡 */
.wiki-main-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  min-height: min(calc(100dvh - 220px), 720px);
  display: flex;
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

.page-list {
  width: clamp(230px, 19vw, 300px);
  flex-shrink: 0;
  border-right: 1px solid var(--line);
  padding: 14px 10px;
  overflow-y: auto;
  background: var(--bg);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.outline-head {
  padding: 4px 8px 8px;
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--t3);
}

.page-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: var(--r3);
  cursor: pointer;
  color: var(--t2);
  font-size: var(--text-body);
  transition: all 0.18s ease;
}

.page-item:hover {
  background: color-mix(in srgb, var(--t4) 10%, transparent);
}

.page-item.active {
  background: var(--tint);
  color: var(--brand);
  font-weight: 600;
  border: 1px solid var(--tint-strong);
}

.page-order {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--t4) 14%, transparent);
  color: var(--t3);
  font-size: var(--text-muted);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.page-item.active .page-order {
  background: var(--brand);
  color: var(--card);
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
  padding: clamp(16px, 2.5vw, 36px);
  overflow-y: auto;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--line);
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chapter-badge {
  font-size: var(--text-muted);
  font-weight: 700;
  color: var(--brand);
  background: var(--tint);
  padding: 2px 8px;
  border-radius: var(--r3);
}

.content-header h3 {
  margin: 0;
  font-size: var(--text-heading);
  color: var(--t1);
}

.content-summary {
  color: var(--t3);
  font-size: var(--text-body);
  margin: 12px 0;
  line-height: 1.6;
}

.content-sources {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--t3);
  font-size: var(--text-muted);
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--r3);
  padding: 6px 12px;
  margin-bottom: 20px;
}

.md-body {
  line-height: 1.8;
  font-size: var(--text-body);
  color: var(--t2);
  word-break: break-word;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  margin: 24px 0 12px;
  color: var(--t1);
  font-weight: 700;
}

.md-body :deep(p) {
  margin: 0 0 14px;
}

.md-body :deep(pre) {
  background: var(--t1);
  color: var(--bg);
  padding: 14px 16px;
  border-radius: var(--r3);
  overflow-x: auto;
  font-size: var(--text-body);
}

.md-body :deep(code) {
  font-family: 'Space Grotesk', Consolas, monospace;
}

.md-body :deep(:not(pre) > code) {
  background: var(--tint);
  color: var(--brand);
  padding: 2px 6px;
  border-radius: var(--r3);
  font-size: var(--text-body);
}

.md-body :deep(table) {
  border-collapse: collapse;
  margin: 16px 0;
  width: 100%;
}

.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid var(--line);
  padding: 8px 14px;
}

.md-body :deep(th) {
  background: var(--bg);
  font-weight: 600;
}

.wiki-empty {
  margin: auto;
  padding: 60px 20px;
}

.custom-scroll::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
.custom-scroll::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--t4) 30%, transparent);
  border-radius: var(--r-pill);
}
.page-item {
  width: 100%;
  text-align: start;
}

@container (max-width: 680px) {
  .wiki-main-card {
    flex-direction: column;
  }

  .page-list {
    width: 100%;
    max-height: 240px;
    border-inline-end: 0;
    border-block-end: 1px solid var(--line);
  }

  .page-content {
    width: 100%;
  }
}
</style>
