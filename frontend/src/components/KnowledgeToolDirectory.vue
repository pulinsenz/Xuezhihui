<template>
  <div class="tool-library" :class="{ 'tool-library--catalog': hasCatalog }">
    <div class="tool-library-heading">
      <h2>知识库</h2>
      <span v-if="!loading && !failed && items.length" class="tool-meta" role="status">{{ matchingKnowledge.length }} 个</span>
    </div>
    <p v-if="loading" class="tool-status" role="status">正在加载知识库…</p>
    <div v-else-if="failed" class="tool-state">
      <p class="tool-status" role="alert">知识库列表加载失败。</p>
      <el-button @click="$emit('retry')">重试</el-button>
    </div>
    <div v-else-if="!items.length" class="tool-state">
      <p class="tool-status">你还没有可选的知识库。</p>
      <router-link class="tool-link" :to="{ name: 'KnowledgeList' }">前往我的知识库</router-link>
    </div>
    <template v-else>
      <el-input
        v-model="search"
        class="tool-search"
        type="search"
        :aria-label="searchLabel"
        :placeholder="searchLabel"
        :prefix-icon="Search"
        @update:model-value="$emit('search')"
      />
      <ul v-if="matchingKnowledge.length" class="tool-library-list" role="list">
        <li v-for="item in matchingKnowledge" :key="item.id">
          <button
            type="button"
            class="tool-row"
            :aria-pressed="hasCatalog ? undefined : isSelected(item)"
            :aria-current="hasCatalog && isSelected(item) ? 'true' : undefined"
            :aria-expanded="hasCatalog ? isSelected(item) && expanded : undefined"
            @click="$emit('select-knowledge', String(item.id))"
          >
            <el-icon v-if="hasCatalog" aria-hidden="true"><component :is="isSelected(item) && expanded ? ArrowDown : ArrowRight" /></el-icon>
            <el-icon aria-hidden="true"><Folder /></el-icon>
            <span class="tool-row-name" :title="item.name">{{ item.name }}</span>
            <span v-if="hasCatalog && isSelected(item) && directory && !directory.loading && !directory.failed" class="tool-meta">{{ directory.entries.length }}</span>
            <el-icon v-if="!hasCatalog" class="tool-row-chevron" aria-hidden="true"><ArrowRight /></el-icon>
          </button>
          <div v-if="hasCatalog && isSelected(item) && expanded" class="tool-branch">
            <p v-if="!directory || directory.loading" class="tool-status" role="status">正在加载{{ entryLabel }}…</p>
            <div v-else-if="directory.failed" class="tool-state">
              <p class="tool-status" role="alert">{{ entryLabel }}列表加载失败。</p>
              <el-button @click="directory.retry()">重试</el-button>
            </div>
            <p v-else-if="!directory.entries.length" class="tool-status" role="status">{{ emptyMessage }}</p>
            <ul v-else-if="matchingEntries.length" :class="tool === 'wiki' ? 'chapter-list' : 'doc-list'" role="list">
              <li v-for="entry in matchingEntries" :key="entry.id">
                <button
                  type="button"
                  class="tool-row"
                  :aria-current="tool === 'wiki' && entry.id === directory.selectedId ? 'true' : undefined"
                  :aria-pressed="tool === 'chunks' ? entry.id === directory.selectedId : undefined"
                  @click="$emit('select-entry', entry.id)"
                >
                  <span v-if="entry.order != null" class="tool-meta" aria-hidden="true">{{ entry.order }}</span>
                  <el-icon v-else-if="tool === 'chunks'" aria-hidden="true"><Document /></el-icon>
                  <span class="tool-row-name" :title="entry.label">{{ entry.label }}</span>
                  <el-tag v-if="entry.failed" size="small" type="danger">失败</el-tag>
                  <span v-else-if="entry.count != null" class="tool-meta">{{ entry.count }} 条</span>
                </button>
              </li>
            </ul>
          </div>
        </li>
      </ul>
      <p v-else class="tool-status" role="status">{{ hasCatalog ? '没有匹配的知识库或' + entryLabel + '。' : '没有匹配的知识库。' }}</p>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown, ArrowRight, Document, Folder, Search } from '@element-plus/icons-vue'

const props = defineProps({
  tool: { type: String, required: true },
  items: { type: Array, required: true },
  selectedId: { type: String, required: true },
  expanded: Boolean,
  loading: Boolean,
  failed: Boolean,
  directory: Object,
})
defineEmits(['select-knowledge', 'select-entry', 'retry', 'search'])

const search = defineModel('search', { type: String, required: true })
const hasCatalog = computed(() => props.tool !== 'retrieval')
const entryLabel = computed(() => props.tool === 'wiki' ? '章节' : '文档')
const emptyMessage = computed(() => props.tool === 'wiki' ? '尚未生成 Wiki 章节。' : '暂无已入库的文档。')
const searchLabel = computed(() => hasCatalog.value ? '搜索知识库或当前' + entryLabel.value : '搜索知识库')
const keyword = computed(() => search.value.trim().toLocaleLowerCase())
const matches = (value) => (value ?? '').toLocaleLowerCase().includes(keyword.value)
const isSelected = (item) => String(item.id) === props.selectedId

const matchingEntries = computed(() => {
  const entries = props.directory?.entries ?? []
  const selected = props.items.find(isSelected)
  return matches(selected?.name) ? entries : entries.filter((entry) => matches(entry.label))
})
const matchingKnowledge = computed(() => props.items.filter((item) =>
  matches(item.name) || (hasCatalog.value && isSelected(item) && matchingEntries.value.length > 0),
))
</script>

<style scoped>
.tool-library--catalog { min-height: 0; }

.tool-library--catalog .tool-library-list {
  display: block;
  min-height: 0;
  max-height: none;
  padding-inline-end: 4px;
}

.tool-library--catalog .tool-library-list > li + li { margin-block-start: 4px; }
.tool-library--catalog .tool-row { gap: 6px; padding-inline: 8px; }
.tool-library--catalog .tool-row[aria-expanded] { font-weight: 650; }
.tool-library--catalog .tool-meta { white-space: nowrap; }

.tool-branch {
  margin: 4px 0 8px 16px;
  padding-inline-start: 8px;
  border-inline-start: 1px solid var(--line);
}

.tool-branch ul { display: grid; gap: 2px; list-style: none; }
.tool-branch .tool-status { padding: 8px; }
</style>
