<template>
  <div class="knowledge-tool" :class="{ 'knowledge-tool--retrieval': tool === 'retrieval' }">
    <div class="tool-card">
      <p v-if="loading" class="tool-status" role="status">正在加载知识库…</p>
      <div v-else-if="failed" class="tool-state">
        <p class="tool-status" role="alert">知识库列表加载失败。</p>
        <el-button @click="loadKnowledgeList">重试</el-button>
      </div>
      <div v-else-if="!knowledgeList.length" class="tool-state">
        <p class="tool-status">你还没有可选的知识库。</p>
        <router-link class="tool-link" :to="{ name: 'KnowledgeList' }">前往我的知识库</router-link>
      </div>
      <div v-else class="tool-picker">
        <label for="knowledge-tool-picker">知识库</label>
        <el-select id="knowledge-tool-picker" v-model="knowledgeId" filterable placeholder="请选择知识库">
          <el-option v-for="item in knowledgeList" :key="item.id" :label="item.name" :value="String(item.id)" />
        </el-select>
      </div>
    </div>

    <template v-if="knowledgeId">
      <div v-if="tool === 'retrieval'" class="tool-card">
        <KnowledgeRetrievalTest :key="knowledgeId" :knowledge-id="knowledgeId" />
      </div>
      <WikiView v-else :key="knowledgeId" :knowledge-id="knowledgeId" />
    </template>
  </div>
</template>

<script setup>
import { defineAsyncComponent, onMounted, ref } from 'vue'
import { listMyKnowledge } from '../api/knowledge'
import KnowledgeRetrievalTest from '../components/KnowledgeRetrievalTest.vue'

defineProps({
  tool: { type: String, required: true },
})

const WikiView = defineAsyncComponent(() => import('./WikiView.vue'))
const knowledgeId = ref('')
const knowledgeList = ref([])
const loading = ref(true)
const failed = ref(false)

const loadKnowledgeList = async () => {
  loading.value = true
  failed.value = false
  try {
    knowledgeList.value = await listMyKnowledge()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadKnowledgeList)
</script>

<style scoped>
.knowledge-tool {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.knowledge-tool--retrieval {
  max-width: 880px;
}

.tool-card {
  min-width: 0;
  padding: clamp(12px, 2vw, 20px);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  background: var(--card);
  box-shadow: var(--shadow-card);
}

.tool-state,
.tool-picker {
  display: grid;
  justify-items: start;
  gap: 8px;
}

.tool-picker .el-select {
  width: min(100%, 360px);
  --el-component-size: 40px;
}

.tool-picker label,
.tool-status,
.tool-link {
  font-size: var(--text-body);
}

.tool-picker label {
  color: var(--t1);
  font-weight: 600;
}

.tool-status {
  color: var(--t2);
}

.tool-link {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  color: var(--brand);
  text-decoration: underline;
}
</style>
