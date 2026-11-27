<template>
  <div>
    <form class="retrieval-form" @submit.prevent="runTest">
      <label :for="`${previewId}-query`">检索问题</label>
      <el-input
        :id="`${previewId}-query`"
        v-model="query"
        type="textarea"
        :rows="3"
        maxlength="500"
        :readonly="loading"
        placeholder="输入你想在此知识库中检索的问题"
      />
      <div class="retrieval-actions">
        <!-- Element Plus 的 loading 会禁用原生按钮，使当前键盘焦点丢失。 -->
        <el-button
          type="primary"
          native-type="submit"
          :disabled="!query.trim()"
          :aria-disabled="loading || !query.trim()"
        >{{ loading ? '检索中…' : '开始检索' }}</el-button>
        <span class="retrieval-note">查看最多 5 条按问答配置排序的切片</span>
      </div>
    </form>

    <p v-if="loading" class="retrieval-status" role="status">正在检索知识库…</p>
    <p v-else-if="failed" class="retrieval-status" role="alert">检索失败，请稍后重试。</p>
    <section v-else-if="result" :aria-labelledby="`${previewId}-results`">
      <h2 :id="`${previewId}-results`" class="retrieval-status" role="status">
        命中 {{ result.hits.length }} 条 · {{ result.elapsedMs }} ms
      </h2>
      <el-empty v-if="!result.hits.length" description="没有检索到可用切片" :image-size="72" />
      <ol v-else ref="hitsList" class="retrieval-hits">
        <li v-for="hit in result.hits" :key="hit.chunkId" class="retrieval-hit">
          <h3>{{ hit.docName || hit.docId }}</h3>
          <div class="retrieval-meta">
            <span v-if="hit.bm25Rank != null">关键词第 {{ hit.bm25Rank }} 名</span>
            <span v-if="hit.vectorRank != null">向量第 {{ hit.vectorRank }} 名</span>
            <span>融合分 {{ hit.fusionScore.toFixed(4) }}</span>
            <span v-if="hit.rerankScore != null">重排分 {{ hit.rerankScore.toFixed(4) }}</span>
          </div>
          <p
            :id="`${previewId}-${hit.chunkId}`"
            :data-chunk-id="hit.chunkId"
            :class="{ 'retrieval-preview': !expandedChunks.has(hit.chunkId) }"
          >{{ hit.text }}</p>
          <button
            v-if="collapsibleChunks.has(hit.chunkId)"
            type="button"
            class="retrieval-toggle"
            :aria-expanded="expandedChunks.has(hit.chunkId)"
            :aria-controls="`${previewId}-${hit.chunkId}`"
            @click="toggleChunk(hit.chunkId)"
          >{{ expandedChunks.has(hit.chunkId) ? '收起正文' : '展开全文' }}</button>
        </li>
      </ol>
    </section>
  </div>
</template>

<script setup>
import { ref, useId, watch } from 'vue'
import { testKnowledgeRetrieval } from '../api/knowledge'

const props = defineProps({ knowledgeId: { type: [String, Number], required: true } })
const query = ref('')
const loading = ref(false)
const result = ref(null)
const failed = ref(false)
const hitsList = ref(null)
const previewId = useId()
const expandedChunks = ref(new Set())
const collapsibleChunks = ref(new Set())

watch(hitsList, (list, _, onCleanup) => {
  if (!list) return
  const observer = new ResizeObserver(() => {
    const collapsible = [...list.querySelectorAll('[data-chunk-id]')]
      .filter((text) => expandedChunks.value.has(text.dataset.chunkId)
        || text.scrollHeight > text.clientHeight)
      .map((text) => text.dataset.chunkId)
    collapsibleChunks.value = new Set(collapsible)
  })
  observer.observe(list)
  onCleanup(() => observer.disconnect())
})

const toggleChunk = (chunkId) => {
  if (expandedChunks.value.has(chunkId)) expandedChunks.value.delete(chunkId)
  else expandedChunks.value.add(chunkId)
}

const runTest = async () => {
  const value = query.value.trim()
  if (!value || loading.value) return
  loading.value = true
  failed.value = false
  result.value = null
  expandedChunks.value.clear()
  collapsibleChunks.value.clear()
  try {
    result.value = await testKnowledgeRetrieval(props.knowledgeId, value)
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.retrieval-form,
.retrieval-hits {
  display: grid;
  gap: 12px;
}

.retrieval-form label {
  color: var(--t1);
  font-size: var(--text-body);
  font-weight: 600;
}

.retrieval-actions,
.retrieval-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.retrieval-actions .el-button {
  min-height: 40px;
}

.retrieval-actions .el-button[aria-disabled='true']:not(:disabled) {
  cursor: wait;
}

.retrieval-actions .el-button:focus-visible,
.retrieval-toggle:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.retrieval-note,
.retrieval-meta {
  color: var(--t3);
  font-size: var(--text-muted);
}

.retrieval-status {
  margin: 24px 0 12px;
  color: var(--t2);
  font-size: var(--text-body);
}

.retrieval-hits {
  padding-inline-start: 24px;
}

.retrieval-hit {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--r2);
  color: var(--t2);
  font-size: var(--text-body);
  overflow-wrap: anywhere;
}

.retrieval-hit h3 {
  margin-bottom: 4px;
  color: var(--t1);
  font-size: var(--text-body);
}

.retrieval-hit p {
  margin-top: 8px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.retrieval-toggle {
  display: none;
  min-height: 40px;
  margin-top: 4px;
  color: var(--brand);
  font-size: var(--text-body);
  font-weight: 600;
  cursor: pointer;
}

.retrieval-toggle:hover {
  color: var(--brand-hover);
  text-decoration: underline;
}

.retrieval-toggle:active {
  color: var(--brand-pressed);
}

@media (max-width: 640px) {
  .retrieval-preview {
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 3;
    overflow: hidden;
  }

  .retrieval-toggle {
    display: inline-block;
  }
}
</style>
