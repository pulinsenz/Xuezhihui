<template>
  <div>
    <form class="tool-section retrieval-form" @submit.prevent="runTest">
      <label :for="`${formId}-query`">检索问题</label>
      <el-input
        :id="`${formId}-query`"
        v-model="query"
        type="textarea"
        :rows="3"
        maxlength="500"
        :readonly="loading"
        placeholder="输入你想在此知识库中检索的问题"
      />
      <div class="retrieval-actions">
        <span class="retrieval-meta">{{ query.length }} / 500</span>
        <!-- 保留提交按钮的焦点，等待期间由 runTest 拦截重复提交。 -->
        <el-button
          type="primary"
          native-type="submit"
          :disabled="!query.trim()"
          :aria-disabled="loading || !query.trim()"
        >{{ loading ? '检索中…' : '开始检索' }}</el-button>
      </div>
    </form>

    <p v-if="loading" class="tool-section retrieval-status" role="status">正在检索知识库…</p>
    <p v-else-if="failed" class="tool-section retrieval-status" role="alert">检索失败，请稍后重试。</p>
    <section v-else-if="result" class="tool-section retrieval-results" :aria-labelledby="`${formId}-results`">
      <div class="tool-section-heading retrieval-heading">
        <h3 :id="`${formId}-results`">命中列表</h3>
        <span class="retrieval-meta" role="status">命中 {{ result.hits.length }} 条 · {{ result.elapsedMs }} ms</span>
      </div>
      <p v-if="!result.hits.length" class="retrieval-status">没有检索到可用切片。</p>
      <ol v-else class="retrieval-hits" role="list">
        <li v-for="(hit, index) in result.hits" :key="hit.chunkId" class="retrieval-hit">
          <details :open="index === 0">
            <summary>
              <span class="retrieval-rank" aria-hidden="true">{{ index + 1 }}</span>
              <span class="retrieval-snippet">
                <strong class="retrieval-source">{{ hit.docName || hit.docId }}</strong>
                <span class="retrieval-meta">融合分 {{ hit.fusionScore.toFixed(4) }}</span>
                <span class="retrieval-preview">{{ hit.text }}</span>
              </span>
              <el-icon class="retrieval-chevron" aria-hidden="true"><ArrowRight /></el-icon>
            </summary>
            <div class="retrieval-content">
              <div
                v-if="hit.bm25Rank != null || hit.vectorRank != null || hit.rerankScore != null"
                class="retrieval-meta retrieval-scores"
              >
                <span v-if="hit.bm25Rank != null">关键词第 {{ hit.bm25Rank }} 名</span>
                <span v-if="hit.vectorRank != null">向量第 {{ hit.vectorRank }} 名</span>
                <span v-if="hit.rerankScore != null">重排分 {{ hit.rerankScore.toFixed(4) }}</span>
              </div>
              <p>{{ hit.text }}</p>
            </div>
          </details>
        </li>
      </ol>
    </section>
  </div>
</template>

<script setup>
import { ref, useId } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { testKnowledgeRetrieval } from '../api/knowledge'

const props = defineProps({ knowledgeId: { type: [String, Number], required: true } })
const query = ref('')
const loading = ref(false)
const result = ref(null)
const failed = ref(false)
const formId = useId()

const runTest = async () => {
  const value = query.value.trim()
  if (!value || loading.value) return
  loading.value = true
  failed.value = false
  result.value = null
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
.retrieval-form {
  display: grid;
  gap: 10px;
}

.retrieval-form label {
  color: var(--t1);
  font-size: var(--text-body);
  font-weight: 650;
}

.retrieval-actions,
.retrieval-scores,
.retrieval-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.retrieval-actions,
.retrieval-heading {
  justify-content: space-between;
}

.retrieval-actions .el-button {
  min-height: 40px;
}

.retrieval-actions .el-button[aria-disabled='true']:not(:disabled) {
  cursor: wait;
}

.retrieval-actions .el-button:focus-visible,
.retrieval-hit summary:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.retrieval-heading h3 {
  font-size: var(--text-heading);
}

.retrieval-meta,
.retrieval-preview,
.retrieval-rank,
.retrieval-chevron {
  color: var(--t3);
  font-size: var(--text-muted);
}

.retrieval-status,
.retrieval-hit {
  color: var(--t2);
  font-size: var(--text-body);
}

.retrieval-hits {
  list-style: none;
}

.retrieval-hit {
  min-width: 0;
  border-top: 1px solid var(--line);
}

.retrieval-hit summary {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 14px;
  align-items: start;
  gap: 10px;
  padding-block: 12px;
  list-style: none;
  cursor: pointer;
}

.retrieval-hit summary::-webkit-details-marker { display: none; }
.retrieval-hit summary > .el-icon { margin-top: 4px; }
.retrieval-hit details[open] .retrieval-chevron { transform: rotate(90deg); }

.retrieval-snippet {
  display: grid;
  gap: 4px;
}

.retrieval-source {
  color: var(--t1);
  overflow-wrap: anywhere;
}

.retrieval-hit summary:hover .retrieval-source,
.retrieval-hit details[open] .retrieval-source {
  color: var(--brand);
}

.retrieval-preview {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.retrieval-hit details[open] .retrieval-preview {
  display: none;
}

.retrieval-content {
  padding: 0 24px 16px 34px;
  overflow-wrap: anywhere;
}

.retrieval-content p {
  line-height: 1.7;
  white-space: pre-wrap;
}

.retrieval-scores {
  margin-bottom: 8px;
}
</style>
