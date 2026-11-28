<template>
  <div class="knowledge-tool">
    <aside class="tool-card tool-library" aria-labelledby="tool-library-title">
      <div class="tool-library-heading">
        <h2 id="tool-library-title">知识库</h2>
        <span v-if="!loading && !failed && knowledgeList.length" class="tool-meta" role="status">{{ matchingKnowledge.length }} 个</span>
      </div>
      <p v-if="loading" class="tool-status" role="status">正在加载知识库…</p>
      <div v-else-if="failed" class="tool-state">
        <p class="tool-status" role="alert">知识库列表加载失败。</p>
        <el-button @click="loadKnowledgeList()">重试</el-button>
      </div>
      <div v-else-if="!knowledgeList.length" class="tool-state">
        <p class="tool-status">你还没有可选的知识库。</p>
        <router-link class="tool-link" :to="{ name: 'KnowledgeList' }">前往我的知识库</router-link>
      </div>
      <template v-else>
        <el-input
          v-model="search"
          class="tool-search"
          type="search"
          aria-label="按名称搜索知识库"
          placeholder="搜索知识库"
          :prefix-icon="Search"
        />
        <ul v-if="matchingKnowledge.length" class="tool-library-list" role="list">
          <li v-for="item in matchingKnowledge" :key="item.id">
            <button type="button" class="tool-row" :aria-pressed="String(item.id) === knowledgeId" @click="knowledgeId = String(item.id)">
              <el-icon aria-hidden="true"><Folder /></el-icon>
              <span class="tool-row-name">{{ item.name }}</span>
              <el-icon class="tool-row-chevron" aria-hidden="true"><ArrowRight /></el-icon>
            </button>
          </li>
        </ul>
        <p v-else class="tool-status">没有匹配的知识库。</p>
      </template>
    </aside>

    <div v-if="selectedKnowledge" class="tool-card tool-work">
      <template v-if="tool === 'retrieval'">
        <header class="tool-work-header"><h2>{{ selectedKnowledge.name }}</h2></header>
        <KnowledgeRetrievalTest :key="knowledgeId" :knowledge-id="knowledgeId" />
      </template>
      <KnowledgeChunks v-else-if="tool === 'chunks'" :key="knowledgeId" :knowledge-id="knowledgeId" :knowledge-name="selectedKnowledge.name" />
      <WikiView v-else :key="knowledgeId" :knowledge-id="knowledgeId" tool-layout />
    </div>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { ArrowRight, Folder, Search } from '@element-plus/icons-vue'
import { listMyKnowledge } from '../api/knowledge'
import KnowledgeChunks from '../components/KnowledgeChunks.vue'
import KnowledgeRetrievalTest from '../components/KnowledgeRetrievalTest.vue'
import { useTabRefresh } from '../utils/tabRefresh'

defineProps({
  tool: { type: String, required: true },
})

const WikiView = defineAsyncComponent(() => import('./WikiView.vue'))
const knowledgeId = ref('')
const knowledgeList = ref([])
const loading = ref(true)
const failed = ref(false)
const search = ref('')
const matchingKnowledge = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  return knowledgeList.value.filter((item) => item.name?.toLocaleLowerCase().includes(keyword))
})
const selectedKnowledge = computed(() => knowledgeList.value.find((item) => String(item.id) === knowledgeId.value))

const loadKnowledgeList = useTabRefresh(async (quiet, isCurrent) => {
  loading.value = !quiet
  failed.value = false
  try {
    const result = await listMyKnowledge()
    if (!isCurrent()) return
    knowledgeList.value = result
    if (!selectedKnowledge.value) {
      knowledgeId.value = result.length ? String(result[0].id) : ''
    }
  } catch {
    if (isCurrent()) failed.value = !quiet
    return false
  } finally {
    if (isCurrent()) loading.value = false
  }
}, ['list'])

onMounted(loadKnowledgeList)
</script>

<style>
.knowledge-tool {
  display: grid;
  grid-template-columns: clamp(180px, 24%, 244px) minmax(0, 1fr);
  align-items: start;
  gap: 16px;
  min-width: 0;
}

.knowledge-tool .tool-card {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: var(--r1);
  background: var(--card);
  box-shadow: var(--shadow-card);
}

.knowledge-tool .tool-library {
  display: grid;
  gap: 12px;
  padding: 16px 12px;
}

.knowledge-tool .tool-library-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.knowledge-tool .tool-library-heading h2,
.knowledge-tool .tool-work-header h2,
.knowledge-tool .tool-section-heading h3 {
  font-size: var(--text-heading);
  font-weight: 650;
  overflow-wrap: anywhere;
}

.knowledge-tool .tool-meta {
  color: var(--t3);
  font-size: var(--text-muted);
}

.knowledge-tool .tool-status {
  font-size: var(--text-body);
  color: var(--t2);
}

.knowledge-tool .tool-state {
  display: grid;
  justify-items: start;
  gap: 8px;
}

.knowledge-tool .tool-link {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  color: var(--brand);
  text-decoration: underline;
}

.knowledge-tool .tool-search .el-input__wrapper {
  min-height: 42px;
  border-radius: var(--r3);
}

.knowledge-tool .tool-library-list {
  display: grid;
  gap: 4px;
  list-style: none;
  max-height: 450px;
  overflow: auto;
}

.knowledge-tool .tool-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  padding: 8px 10px;
  border-radius: var(--r3);
  text-align: start;
  font-size: var(--text-body);
  color: var(--t2);
  cursor: pointer;
}

.knowledge-tool .tool-row:hover { background: var(--bg); }
.knowledge-tool .tool-row:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
.knowledge-tool .tool-row[aria-pressed='true'] { background: var(--tint); color: var(--brand); font-weight: 650; }
.knowledge-tool .tool-row:active { background: var(--tint-strong); }
.knowledge-tool .tool-row[aria-pressed='true']::before {
  content: '';
  position: absolute;
  inset: 10px auto 10px 0;
  width: 2px;
  background: var(--brand);
  border-radius: var(--r3);
}

.knowledge-tool .tool-row .el-icon { flex: none; }
.knowledge-tool .tool-row-name { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.knowledge-tool .tool-row-chevron { color: var(--t3); }
.knowledge-tool .tool-work { container: knowledge-work / inline-size; }

.knowledge-tool .tool-work-header,
.knowledge-tool .tool-section {
  padding: clamp(14px, 2vw, 20px);
}

.knowledge-tool .tool-work-header { border-bottom: 1px solid var(--line); }
.knowledge-tool .tool-work-header .tool-meta { margin-top: 4px; }
.knowledge-tool .tool-section + .tool-section { border-top: 1px solid var(--line); }
.knowledge-tool .tool-section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-bottom: 8px;
}

@container (max-width: 570px) {
  .knowledge-tool { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .knowledge-tool .tool-library-list { max-height: 154px; }
}
</style>
