<template>
  <div class="knowledge-chunks">
    <header class="tool-work-header">
      <h2>{{ knowledgeName }}</h2>
      <p v-if="!docsLoading && !docsFailed" class="tool-meta">{{ docs.length }} 份已入库文档</p>
    </header>

    <section class="tool-section" aria-labelledby="chunk-heading">
      <div class="tool-section-heading">
        <h3 id="chunk-heading">{{ selectedDoc?.name || '切片正文' }}</h3>
        <span v-if="!chunksLoading && !chunksFailed && chunks.length" class="tool-meta" role="status">
          第 {{ pageStart + 1 }}–{{ pageStart + visibleChunks.length }} 条，共 {{ chunks.length }} 条
        </span>
      </div>
      <p v-if="docsLoading" class="tool-status" role="status">正在加载文档…</p>
      <div v-else-if="docsFailed" class="status-actions">
        <p class="tool-status" role="alert">文档列表加载失败。</p>
        <el-button @click="loadDocs()">重试</el-button>
      </div>
      <p v-else-if="!docs.length" class="tool-status" role="status">这个知识库暂无已入库的文档。</p>
      <p v-else-if="chunksLoading" class="tool-status" role="status">正在读取切片…</p>
      <div v-else-if="chunksFailed" class="status-actions">
        <p class="tool-status" role="alert">切片读取失败。</p>
        <el-button @click="chunkAttempt++">重试</el-button>
      </div>
      <p v-else-if="!chunks.length" class="tool-status" role="status">该文档暂无可查看的切片。</p>
      <template v-else>
        <ol class="chunk-list" :start="pageStart + 1" role="list">
          <li v-for="(chunk, index) in visibleChunks" :key="pageStart + index" class="chunk">
            <span class="chunk-number">切片 {{ pageStart + index + 1 }}</span>
            <p>{{ chunk }}</p>
          </li>
        </ol>
        <nav v-if="chunks.length > pageSize" class="chunk-pager" aria-label="切片分页">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="chunks.length"
            :pager-count="5"
            layout="prev, pager, next"
          />
        </nav>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getDocChunks, listDocs } from '../api/knowledge'
import { useTabRefresh } from '../utils/tabRefresh'

const props = defineProps({
  knowledgeId: { type: String, required: true },
  knowledgeName: { type: String, required: true },
})

const docs = ref([])
const docsLoading = ref(true)
const docsFailed = ref(false)
const docId = ref('')
const selectedDoc = computed(() => docs.value.find((doc) => String(doc.id) === docId.value))
const chunks = ref([])
const chunksLoading = ref(false)
const chunksFailed = ref(false)
const chunkAttempt = ref(0)

const pageSize = 20
const page = ref(1)
const pageStart = computed(() => (page.value - 1) * pageSize)
const visibleChunks = computed(() => chunks.value.slice(pageStart.value, pageStart.value + pageSize))

let chunkRequest = 0

const loadDocs = useTabRefresh(async (quiet, isCurrent) => {
  const selectedDoc = docId.value
  docsLoading.value = !quiet
  docsFailed.value = false
  try {
    const result = await listDocs(props.knowledgeId, 0, 'vectorized')
    if (!isCurrent()) return
    docs.value = result
    if (!result.some((doc) => String(doc.id) === docId.value)) {
      docId.value = result.length ? String(result[0].id) : ''
    } else if (quiet && selectedDoc && selectedDoc === docId.value && !chunksLoading.value) {
      return await loadChunks(selectedDoc, true, isCurrent)
    }
  } catch {
    if (isCurrent()) docsFailed.value = !quiet
    return false
  } finally {
    if (isCurrent()) docsLoading.value = false
  }
}, ['docs'])

const loadChunks = async (id, quiet, isCurrent = () => true) => {
  const requestId = ++chunkRequest
  const current = () => isCurrent() && id === docId.value && requestId === chunkRequest
  if (!quiet) {
    chunks.value = []
    page.value = 1
    chunksFailed.value = false
    chunksLoading.value = Boolean(id)
  }
  if (!id) return

  try {
    const result = await getDocChunks(props.knowledgeId, id)
    if (current()) {
      chunks.value = result
      chunksFailed.value = false
      page.value = Math.min(page.value, Math.max(1, Math.ceil(result.length / pageSize)))
    }
  } catch {
    if (current() && !quiet) chunksFailed.value = true
    return false
  } finally {
    if (current()) chunksLoading.value = false
  }
}

watch([docId, chunkAttempt], ([id], _, onCleanup) => {
  // 切换文档或离开页面后，忽略上一次查询的响应。
  let current = true
  onCleanup(() => { current = false })
  loadChunks(id, false, () => current)
})

const directory = computed(() => ({
  entries: docs.value.map((doc) => ({
    id: String(doc.id),
    label: doc.name,
    count: String(doc.id) === docId.value && !chunksLoading.value && !chunksFailed.value ? chunks.value.length : null,
  })),
  selectedId: docId.value,
  loading: docsLoading.value,
  failed: docsFailed.value,
  retry: () => loadDocs(),
  select: (id) => { docId.value = id },
}))
defineExpose({ directory })

onMounted(loadDocs)
</script>

<style scoped>
.status-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.status-actions .el-button {
  min-height: 40px;
}

.chunk-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.chunk-pager .el-pagination {
  --el-pagination-button-height: 40px;
  --el-pagination-button-width: 40px;
  --el-pagination-border-radius: var(--r3);
}

@container knowledge-work (max-width: 370px) {
  .chunk-pager .el-pagination {
    --el-pagination-button-width: 32px;
  }
}

.chunk-list {
  list-style: none;
}

.chunk {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 12px;
  padding-block: 12px;
  border-block-end: 1px solid var(--line);
}

.chunk-number {
  color: var(--t3);
  font-weight: 600;
  font-size: var(--text-muted);
}

.chunk p {
  color: var(--t2);
  font-size: var(--text-body);
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@container knowledge-work (max-width: 370px) {
  .chunk { grid-template-columns: minmax(0, 1fr); gap: 4px; }
}
</style>
