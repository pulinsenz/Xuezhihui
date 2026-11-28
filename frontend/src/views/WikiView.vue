<template>
  <div ref="wikiPage" class="wiki-page" :class="{ 'wiki-page--tool': toolLayout }">
    <header class="header-card">
      <div class="header-left">
        <el-button v-if="!props.knowledgeId" :icon="ArrowLeft" circle class="back-btn" aria-label="返回知识库" @click="goBack" />
        <div class="head-info">
          <div class="title-row">
            <h2 ref="wikiTitle" tabindex="-1">{{ toolLayout ? '' : 'AI Wiki · ' }}{{ knowledge?.name || '知识库' }}</h2>
            <span v-if="knowledge?.isPublic === 1" class="status-pill pill-public">公开</span>
          </div>
          <div class="meta-row">
            <span>{{ pages.length }} 个章节</span>
          </div>
        </div>
      </div>
      <div class="actions">
        <el-button v-if="!toolLayout" :icon="Refresh" :disabled="generating" @click="loadAll()">刷新</el-button>
        <el-button
          v-if="canManage"
          type="primary"
          class="generate-btn"
          :icon="toolLayout ? undefined : MagicStick"
          :loading="generating"
          @click="handleGenerate"
        >{{ pages.length ? '重新生成 Wiki' : toolLayout ? '生成 Wiki' : '生成 AI Wiki' }}</el-button>
        <el-dropdown v-if="toolLayout" trigger="click" @command="handleToolCommand">
          <el-button :disabled="generating" :icon="ArrowDown">更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="refresh">刷新章节</el-dropdown-item>
              <el-dropdown-item v-if="canManage && pages.length" command="delete" divided>删除 Wiki</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button
          v-else-if="canManage && pages.length"
          type="danger"
          plain
          :icon="Delete"
          :disabled="generating"
          @click="handleDeleteWiki"
        >删除 Wiki</el-button>
      </div>
    </header>

    <!-- 主阅读与大纲区域：弹性双栏，大缩放下高度自适应 -->
    <div ref="readingCard" v-loading="loading" class="wiki-main-card">
      <template v-if="pages.length">
        <aside v-if="!toolLayout" class="wiki-sidebar" aria-label="章节目录">
          <div ref="outline" class="page-list custom-scroll">
            <div class="outline-head">
              <span>章节目录 ({{ pages.length }})</span>
            </div>
            <ol class="chapter-list" role="list">
              <li v-for="page in pages" :key="page.id">
                <button
                  type="button"
                  class="page-item"
                  :class="{ active: String(currentId) === String(page.id) }"
                  :aria-current="String(currentId) === String(page.id) ? 'true' : undefined"
                  @click="navigatePage(page.id)"
                >
                  <span class="page-order">{{ page.sortOrder }}</span>
                  <span class="page-title" :title="page.title">{{ page.title }}</span>
                  <el-tag v-if="page.status === 'FAILED'" size="small" type="danger">失败</el-tag>
                </button>
              </li>
            </ol>
          </div>
          <div class="outline-actions">
            <el-button
              v-show="outlinePinned"
              :icon="Top"
              class="back-to-top"
              aria-label="返回最上"
              title="返回最上"
              @click="backToTop"
            />
          </div>
        </aside>

        <section class="page-content custom-scroll" aria-label="Wiki 正文">
          <template v-if="current">
            <div class="content-header">
              <div class="title-wrap">
                <span v-if="!toolLayout" class="chapter-badge">Chapter {{ current.sortOrder }}</span>
                <h3 ref="chapterTitle" tabindex="-1">{{ current.title }}</h3>
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

            <el-alert
              v-if="current.status === 'FAILED'"
              type="warning"
              :closable="false"
              :title="`该页生成失败：${current.errorMsg || '未知原因'}，可尝试重新生成整个 Wiki`"
            />
            <div v-else class="md-body" @click="followWikiLink" v-html="rendered" />
            <p v-if="sourceNames.length" class="content-sources">来源文档：{{ sourceNames.join('、') }}</p>
          </template>
        </section>
      </template>

      <el-empty v-else-if="!loading" description="尚未生成 Wiki 知识大纲" class="wiki-empty">
        <template v-if="canManage && !toolLayout">
          <el-button type="primary" class="generate-btn" :icon="MagicStick" :loading="generating" @click="handleGenerate">
            基于知识库文档生成 Wiki
          </el-button>
        </template>
        <p v-else-if="!canManage" class="empty-tip">知识库作者尚未生成 Wiki，请稍后访问</p>
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
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowLeft, Delete, Edit, MagicStick, Refresh, Top } from '@element-plus/icons-vue'
import { renderWikiMarkdown } from '../utils/wikiMarkdown'
import { markKnowledgeChanged, useTabRefresh } from '../utils/tabRefresh'
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
const props = defineProps({ knowledgeId: String, toolLayout: Boolean })
const knowledgeId = props.knowledgeId ?? route.params.id

const knowledge = ref(null)
const pages = ref([])
const loading = ref(false)
const directoryLoading = ref(true)
const directoryFailed = ref(false)
const generating = ref(false)
const currentId = ref(null)
const current = ref(null)
const docNameMap = ref({})

const wikiPage = ref(null)
const wikiTitle = ref(null)
const chapterTitle = ref(null)
const readingCard = ref(null)
const outline = ref(null)
const outlinePinned = ref(false)
const active = ref(true)

const syncOutline = () => {
  if (!outline.value) return
  // 目录吸顶后，阅读卡顶部仍会随正文向上移动。
  const cardTop = readingCard.value.getBoundingClientRect().top + readingCard.value.clientTop
  outlinePinned.value = outline.value.getBoundingClientRect().top - cardTop > 1
}

const backToTop = () => {
  const scrollContainer = wikiPage.value?.closest('.page-body')
  const scroller = scrollContainer?.scrollTop > 0 ? scrollContainer : window
  const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'
  wikiTitle.value?.focus({ preventScroll: true })
  scroller.scrollTo({ top: 0, behavior })
}

watch([outline, active], ([element, isActive], _, onCleanup) => {
  outlinePinned.value = false
  if (!element || !isActive) return

  const scrollContainer = wikiPage.value?.closest('.page-body')
  scrollContainer?.addEventListener('scroll', syncOutline, { passive: true })
  window.addEventListener('scroll', syncOutline, { passive: true })
  const resizeObserver = new ResizeObserver(syncOutline)
  resizeObserver.observe(wikiPage.value)
  if (scrollContainer) resizeObserver.observe(scrollContainer)
  syncOutline()

  onCleanup(() => {
    resizeObserver.disconnect()
    scrollContainer?.removeEventListener('scroll', syncOutline)
    window.removeEventListener('scroll', syncOutline)
  })
}, { flush: 'post' })

const isOwner = computed(() => knowledge.value?.isOwner === true)
const isMember = computed(() => knowledge.value?.isMember === true)
const canManage = computed(() => isOwner.value || isMember.value)

const editDialogVisible = ref(false)
const editing = ref(false)
const editForm = ref({ title: '', content: '' })

onActivated(() => { active.value = true })
onDeactivated(() => {
  active.value = false
  editDialogVisible.value = false
})

const wikiPageLocation = (pageId) => ({
  name: 'KnowledgeWiki',
  params: { id: knowledgeId },
  query: { page: String(pageId) },
})

const rendered = computed(() => {
  if (!current.value?.content) return ''
  return renderWikiMarkdown(current.value.content, pages.value, currentId.value,
    (pageId) => router.resolve(wikiPageLocation(pageId)).href)
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

const loadKnowledge = async (isCurrent) => {
  const result = await getKnowledge(knowledgeId)
  if (isCurrent()) knowledge.value = result
}

const findPage = (pageId) => pages.value.find((page) => String(page.id) === String(pageId))

let pageRequest = 0
let pageListRequest = 0
let requestedPageId

const loadPages = async (isCurrent, quiet) => {
  const request = ++pageListRequest
  const isListCurrent = () => isCurrent() && request === pageListRequest
  directoryLoading.value = !quiet
  try {
    const result = await listWikiPages(knowledgeId)
    if (!isListCurrent()) return
    pages.value = result
    directoryFailed.value = false
  } catch (error) {
    if (isListCurrent() && !quiet) directoryFailed.value = true
    throw error
  } finally {
    if (isListCurrent()) directoryLoading.value = false
  }
  if (!findPage(currentId.value)) {
    current.value = null
    currentId.value = null
  }
  const requested = props.knowledgeId ? requestedPageId : route.query?.page
  return selectPage((findPage(requested) || pages.value[0])?.id)
}

const loadDocNames = async (isCurrent) => {
  try {
    const docs = await listDocs(knowledgeId, 0, 'all')
    if (isCurrent()) docNameMap.value = Object.fromEntries(docs.map((doc) => [String(doc.id), doc.name]))
  } catch {
    // 文档名仅用于来源展示，失败不阻塞页面
  }
}

const loadAll = useTabRefresh(async (quiet, isCurrent) => {
  loading.value = !quiet
  try {
    const results = await Promise.allSettled([loadKnowledge(isCurrent), loadPages(isCurrent, quiet), loadDocNames(isCurrent)])
    return results.every((result) => result.status === 'fulfilled' && result.value !== false)
  } finally {
    if (isCurrent()) loading.value = false
  }
}, ['list', 'docs', 'wiki'])

const selectPage = async (pageId, focus = false) => {
  const request = ++pageRequest
  requestedPageId = pageId
  if (!pageId) {
    current.value = null
    currentId.value = null
    return
  }
  let page
  try {
    page = await getWikiPage(knowledgeId, pageId)
  } catch {
    // 请求层已展示失败提示，保留正在阅读的章节。
    return false
  }
  if (disposed || request !== pageRequest) return
  currentId.value = pageId
  current.value = page
  if (focus) {
    await nextTick()
    if (active.value && request === pageRequest) chapterTitle.value?.focus()
  }
}

const navigatePage = (pageId, focus = false) => {
  if (props.knowledgeId || String(route.query?.page) === String(pageId)) {
    return selectPage(pageId, focus)
  }
  return router.push(wikiPageLocation(pageId))
}

const directory = computed(() => ({
  entries: pages.value.map((page) => ({
    id: String(page.id),
    label: page.title,
    order: page.sortOrder,
    failed: page.status === 'FAILED',
  })),
  selectedId: String(currentId.value ?? ''),
  loading: directoryLoading.value,
  failed: directoryFailed.value,
  retry: () => loadAll(),
  select: navigatePage,
}))
defineExpose({ directory })

const followWikiLink = (event) => {
  const pageId = event.target.closest?.('a[data-wiki-page]')?.dataset.wikiPage
  if (!pageId || event.defaultPrevented || event.button !== 0 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
  event.preventDefault()
  return navigatePage(pageId, true)
}

watch(() => route.query?.page, (pageId) => {
  if (props.knowledgeId || route.name !== 'KnowledgeWiki' || !pages.value.length) return
  return selectPage((findPage(pageId) || pages.value[0]).id, true)
})

let pollTimer = null
let disposed = false

const startPolling = (taskId) => {
  generating.value = true
  const timer = setInterval(async () => {
    try {
      const task = await getTask(taskId)
      if (disposed || pollTimer !== timer) return
      if (task.status === 'SUCCESS' || task.status === 'FAILED') {
        clearInterval(timer)
        pollTimer = null
        generating.value = false
        if (task.status === 'SUCCESS') {
          markKnowledgeChanged('wiki')
          ElMessage.success('Wiki 生成完成')
          await loadAll(true)
        } else {
          ElMessage.error(task.message || 'Wiki 生成失败，原有内容已保留')
        }
      }
    } catch {
      // 任务过期等瞬时错误：下轮重试
    }
  }, 2000)
  pollTimer = timer
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
  pageListRequest++
  directoryLoading.value = false
  directoryFailed.value = false
  pages.value = []
  await selectPage()
}

const handleToolCommand = (command) => command === 'refresh' ? loadAll() : handleDeleteWiki()

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
    await loadAll(true)
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
  /* 保留圆角裁切，让目录相对主内容区吸顶。 */
  overflow: clip;
  box-shadow: var(--shadow-card);
}

.wiki-sidebar {
  --outline-action-size: 40px;
  --outline-action-gap: 8px;
  width: clamp(230px, 19vw, 300px);
  flex-shrink: 0;
  border-inline-end: 1px solid var(--line);
  background: var(--bg);
  display: flex;
  flex-direction: column;
}

.page-list {
  position: sticky;
  top: 0;
  max-height: calc(
    100dvh - var(--topbar-height, 0px) - var(--page-body-padding-top, 0px) - var(--page-body-padding-bottom, 0px)
    - var(--outline-action-size) - var(--outline-action-gap)
  );
  padding: 14px 10px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.outline-actions {
  position: sticky;
  bottom: calc(20px - var(--page-body-padding-bottom, 0px));
  align-self: center;
  flex-shrink: 0;
  height: var(--outline-action-size);
  margin-block: auto var(--outline-action-gap);
}

.back-to-top {
  --el-button-bg-color: var(--card);
  --el-button-border-color: var(--line);
  --el-button-text-color: var(--t2);
  --el-button-hover-bg-color: var(--tint);
  --el-button-hover-border-color: var(--brand);
  --el-button-hover-text-color: var(--brand-hover);
  --el-button-active-bg-color: var(--tint-strong);
  --el-button-active-border-color: var(--brand-pressed);
  --el-button-active-text-color: var(--brand-pressed);
  width: var(--outline-action-size);
  height: var(--outline-action-size);
  padding: 0;
  border-radius: var(--r3);
  font-size: var(--text-heading);
}

.back-to-top:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.outline-head {
  padding: 4px 8px 8px;
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--t3);
}

.chapter-list {
  list-style: none;
  display: grid;
  gap: 4px;
}

.page-item {
  display: flex;
  align-items: center;
  flex-shrink: 0;
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

.md-body :deep(a[data-wiki-page]) {
  color: var(--brand);
  text-decoration: underline;
  text-underline-offset: 0.15em;
}

.md-body :deep(a[data-wiki-page]:hover) {
  color: var(--brand-hover);
}

.md-body :deep(a[data-wiki-page]:focus-visible) {
  outline: 2px solid var(--brand);
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

.wiki-page--tool {
  gap: 0;
}

.wiki-page--tool .header-card,
.wiki-page--tool .wiki-main-card {
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.wiki-page--tool .header-card {
  padding: clamp(14px, 2vw, 20px);
  border-block-end: 1px solid var(--line);
}

.wiki-page--tool .header-left { flex: 1 1 160px; }
.wiki-page--tool .actions .el-button,
.wiki-page--tool .edit-btn { min-height: 40px; }
.wiki-page--tool .wiki-main-card { min-height: 0; }

.wiki-page--tool .page-content { padding: clamp(14px, 2vw, 20px); }
.wiki-page--tool .content-header { flex-wrap: wrap; border: 0; padding: 0; }
.wiki-page--tool .content-header h3 { overflow-wrap: anywhere; }
.wiki-page--tool .content-summary { color: var(--t2); }

.wiki-page--tool .content-sources {
  display: block;
  margin: 16px 0 0;
  border: 0;
  border-block-start: 1px solid var(--line);
  border-radius: 0;
  padding: 12px 0 0;
  background: transparent;
  overflow-wrap: anywhere;
}

@container (max-width: 680px) {
  .wiki-main-card {
    flex-direction: column;
  }

  .wiki-sidebar {
    position: sticky;
    top: var(--wiki-outline-top, 0px);
    z-index: 1;
    width: 100%;
    border-inline-end: 0;
    border-block-end: 1px solid var(--line);
  }

  .page-list {
    position: static;
    max-height: min(240px, 35dvh);
  }

  .outline-head {
    min-height: var(--outline-action-size);
    padding-inline-end: calc(var(--outline-action-size) + 8px);
  }

  .outline-actions {
    position: absolute;
    inset: 8px 10px auto auto;
    margin: 0;
  }

  .page-content {
    width: 100%;
  }
}

</style>
