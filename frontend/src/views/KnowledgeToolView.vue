<template>
  <div class="knowledge-tool" :class="{ 'knowledge-tool--catalog': hasCatalog, 'knowledge-tool--with-content': hasCatalog && selectedKnowledge && !failed }">
    <aside class="tool-card tool-directory" :aria-label="hasCatalog ? '知识库与目录' : '知识库'">
      <KnowledgeToolDirectory
        v-model:search="search"
        v-bind="directoryProps"
        @select-knowledge="selectKnowledge"
        @select-entry="selectEntry"
        @search="expanded = true"
        @retry="loadKnowledgeList()"
      />
    </aside>

    <div v-if="selectedKnowledge" ref="reader" class="tool-card tool-work">
      <div v-if="hasCatalog" class="tool-mobile-directory">
        <el-button :icon="List" aria-haspopup="dialog" @click="directoryOpen = true">知识库与目录</el-button>
      </div>
      <component
        :is="tool === 'wiki' ? WikiView : KnowledgeChunks"
        v-if="hasCatalog"
        :key="knowledgeId"
        ref="activeContent"
        :knowledge-id="knowledgeId"
        v-bind="tool === 'wiki' ? { toolLayout: true } : { knowledgeName: selectedKnowledge.name }"
      />
      <template v-else>
        <header class="tool-work-header"><h2>{{ selectedKnowledge.name }}</h2></header>
        <KnowledgeRetrievalTest :key="knowledgeId" :knowledge-id="knowledgeId" />
      </template>
    </div>

    <el-drawer v-if="hasCatalog" v-model="directoryOpen" append-to-body class="tool-directory-drawer" title="知识库与目录" size="min(360px, 100%)">
      <KnowledgeToolDirectory
        v-if="directoryOpen"
        v-model:search="search"
        v-bind="directoryProps"
        @select-knowledge="selectKnowledge"
        @select-entry="selectEntry"
        @search="expanded = true"
        @retry="loadKnowledgeList()"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onDeactivated, onMounted, ref } from 'vue'
import { List } from '@element-plus/icons-vue'
import { listMyKnowledge } from '../api/knowledge'
import KnowledgeChunks from '../components/KnowledgeChunks.vue'
import KnowledgeRetrievalTest from '../components/KnowledgeRetrievalTest.vue'
import KnowledgeToolDirectory from '../components/KnowledgeToolDirectory.vue'
import { useTabRefresh } from '../utils/tabRefresh'

const props = defineProps({
  tool: { type: String, required: true },
})

const WikiView = defineAsyncComponent(() => import('./WikiView.vue'))
const knowledgeId = ref('')
const knowledgeList = ref([])
const loading = ref(true)
const failed = ref(false)
const search = ref('')
const activeContent = ref(null)
const reader = ref(null)
const expanded = ref(true)
const directoryOpen = ref(false)
const hasCatalog = computed(() => props.tool !== 'retrieval')
const directoryProps = computed(() => ({
  tool: props.tool,
  items: knowledgeList.value,
  selectedId: knowledgeId.value,
  expanded: expanded.value,
  loading: loading.value,
  failed: failed.value,
  directory: activeContent.value?.directory,
}))
const selectedKnowledge = computed(() => knowledgeList.value.find((item) => String(item.id) === knowledgeId.value))

const selectKnowledge = (id) => {
  if (id === knowledgeId.value) {
    if (hasCatalog.value) expanded.value = !expanded.value
    return
  }
  knowledgeId.value = id
  expanded.value = true
}

const selectEntry = (id) => {
  const directory = activeContent.value?.directory
  if (!directory) return
  directory.select(id)
  directoryOpen.value = false
  reader.value?.scrollIntoView?.({ block: 'start' })
}

const loadKnowledgeList = useTabRefresh(async (quiet, isCurrent) => {
  loading.value = !quiet
  failed.value = false
  try {
    const result = await listMyKnowledge()
    if (!isCurrent()) return
    knowledgeList.value = result
    if (!selectedKnowledge.value) {
      selectKnowledge(result.length ? String(result[0].id) : '')
    }
  } catch {
    if (isCurrent()) failed.value = !quiet
    return false
  } finally {
    if (isCurrent()) loading.value = false
  }
}, ['list'])

onMounted(loadKnowledgeList)
onDeactivated(() => { directoryOpen.value = false })
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

.knowledge-tool--catalog {
  grid-template-columns: clamp(204px, 24%, 256px) minmax(0, 1fr);
}

.knowledge-tool--catalog .tool-directory {
  position: sticky;
  inset-block-start: 0;
  display: flex;
  flex-direction: column;
  max-block-size: calc(100dvh - var(--topbar-height, 60px) - var(--page-body-padding-top, 18px) - var(--page-body-padding-bottom, 28px));
  border: 0;
  background: transparent;
  box-shadow: none;
}

.knowledge-tool .tool-library,
.tool-directory-drawer .tool-library {
  display: grid;
  gap: 12px;
  padding: 16px 12px;
}

.tool-directory-drawer .tool-library { padding: 0; }
.knowledge-tool .tool-library--catalog,
.tool-directory-drawer .tool-library--catalog {
  display: flex;
  flex-direction: column;
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-library-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-library-heading h2,
.knowledge-tool .tool-work-header h2,
.knowledge-tool .tool-section-heading h3 {
  font-size: var(--text-heading);
  font-weight: 650;
  overflow-wrap: anywhere;
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-meta {
  color: var(--t3);
  font-size: var(--text-muted);
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-status {
  font-size: var(--text-body);
  color: var(--t2);
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-state {
  display: grid;
  justify-items: start;
  gap: 8px;
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-link {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  color: var(--brand);
  text-decoration: underline;
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-search .el-input__wrapper {
  min-height: 42px;
  border-radius: var(--r3);
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-library-list {
  display: grid;
  gap: 4px;
  list-style: none;
  max-height: 450px;
  overflow: auto;
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-row {
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

:is(.knowledge-tool, .tool-directory-drawer) .tool-row:hover { background: var(--tint); }
:is(.knowledge-tool, .tool-directory-drawer) .tool-row:focus-visible { outline: 2px solid var(--brand); outline-offset: -2px; }
:is(.knowledge-tool, .tool-directory-drawer) .tool-row:is([aria-pressed='true'], [aria-current='true']) { background: var(--tint); color: var(--brand); font-weight: 650; }
:is(.knowledge-tool, .tool-directory-drawer) .tool-row:active { background: var(--tint-strong); }
:is(.knowledge-tool, .tool-directory-drawer) .tool-row:is([aria-pressed='true'], [aria-current='true'])::before {
  content: '';
  position: absolute;
  inset: 10px auto 10px 0;
  width: 2px;
  background: var(--brand);
  border-radius: var(--r3);
}

:is(.knowledge-tool, .tool-directory-drawer) .tool-row .el-icon { flex: none; }
:is(.knowledge-tool, .tool-directory-drawer) .tool-row-name { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.knowledge-tool .tool-row-chevron { color: var(--t3); }
.knowledge-tool .tool-work { container: knowledge-work / inline-size; }
.knowledge-tool .tool-mobile-directory { display: none; }
.knowledge-tool .tool-mobile-directory .el-button { min-height: 40px; }

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

@container page-body (max-width: 570px) {
  .knowledge-tool { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .knowledge-tool:not(.knowledge-tool--catalog) .tool-library-list { max-height: 154px; }
  .knowledge-tool--catalog .tool-directory { position: static; max-block-size: none; }
  .knowledge-tool--with-content .tool-directory { display: none; }
  .knowledge-tool--catalog .tool-work { scroll-margin-block-start: var(--topbar-height, 60px); }
  .knowledge-tool .tool-mobile-directory {
    display: block;
    padding: 8px clamp(14px, 2vw, 20px);
    border-block-end: 1px solid var(--line);
  }
}

.tool-directory-drawer .el-drawer__header { margin-bottom: 0; }
.tool-directory-drawer .el-drawer__body { padding: 12px 16px; }
.tool-directory-drawer .tool-library--catalog .tool-library-list { max-height: none; }
</style>
