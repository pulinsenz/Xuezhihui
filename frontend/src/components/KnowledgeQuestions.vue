<template>
  <section class="questions-section" aria-labelledby="questions-title" v-loading="loading">
    <div class="questions-heading">
      <h3 id="questions-title">常用问题</h3>
      <el-button v-if="isOwner" :disabled="loading || !!loadError" @click="openEditor">编辑常用问题</el-button>
    </div>
    <div v-if="loadError" role="alert">
      {{ loadError }} <el-button text @click="load">重新加载</el-button>
    </div>
    <ol v-else-if="questions.length">
      <li v-for="question in questions" :key="question">{{ question }}</li>
    </ol>
    <p v-else class="questions-hint">{{ isOwner ? '添加常用问题，用户选择此知识库后可一键提问。' : '作者尚未配置常用问题。' }}</p>

    <el-dialog v-model="editing" title="编辑常用问题" width="min(560px, 92vw)" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <p class="questions-hint">最多 8 个，每个 200 字以内；按列表顺序展示，留空列表并保存可清空。</p>
      <div v-for="(question, index) in draft" :key="index" class="question-edit-row">
        <label :for="`question-${index}`">问题 {{ index + 1 }}</label>
        <el-input :id="`question-${index}`" v-model="draft[index]" :maxlength="200" show-word-limit :disabled="saving" />
        <el-button :disabled="saving" :aria-label="`移除问题 ${index + 1}`" @click="draft.splice(index, 1)">移除</el-button>
      </div>
      <p v-if="validationError" class="questions-error" role="alert">{{ validationError }}</p>
      <el-button :disabled="draft.length >= 8 || saving" @click="draft.push('')">添加问题</el-button>
      <template #footer>
        <el-button :disabled="saving" @click="editing = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存常用问题</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listKnowledgeQuestions, saveKnowledgeQuestions } from '../api/knowledge'

const props = defineProps({ knowledgeId: { type: [String, Number], required: true }, isOwner: Boolean })
const questions = ref([])
const draft = ref([])
const loading = ref(false)
const loadError = ref('')
const editing = ref(false)
const saving = ref(false)
const attempted = ref(false)
const validationError = computed(() => {
  if (!attempted.value) return ''
  const values = draft.value.map((q) => q.trim())
  if (values.some((q) => !q || q.length > 200)) return '请填写每个问题，长度为 1–200 个字符；不需要的问题请移除。'
  if (new Set(values).size !== values.length) return '问题不能重复，请修改后保存。'
  return ''
})

let requestVersion = 0
const load = async () => {
  const version = ++requestVersion
  loading.value = true
  loadError.value = ''
  try {
    const result = await listKnowledgeQuestions(props.knowledgeId)
    if (version === requestVersion) questions.value = result
  } catch {
    if (version === requestVersion) loadError.value = '常用问题加载失败。'
  } finally { if (version === requestVersion) loading.value = false }
}
const openEditor = () => {
  draft.value = [...questions.value]
  attempted.value = false
  editing.value = true
}
const save = async () => {
  attempted.value = true
  if (validationError.value || saving.value) return
  saving.value = true
  try {
    const values = draft.value.map((q) => q.trim())
    await saveKnowledgeQuestions(props.knowledgeId, values)
    questions.value = values
    editing.value = false
    ElMessage.success('常用问题已保存')
  } catch { /* 请求层提示，保留表单可重试 */ }
  finally { saving.value = false }
}
watch(() => props.knowledgeId, () => { questions.value = []; editing.value = false; load() }, { immediate: true })
</script>

<style scoped>
.questions-section { padding: 20px; margin-bottom: 20px; background: var(--card); border-radius: var(--r1); box-shadow: var(--shadow-card); color: var(--t2); font-size: var(--text-body); }
.questions-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.questions-heading h3 { font-size: var(--text-heading); color: var(--t1); }
ol { padding-left: 20px; line-height: 1.8; overflow-wrap: anywhere; }
.questions-hint { color: var(--t3); font-size: var(--text-body); margin-bottom: 10px; line-height: 1.6; }
.question-edit-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 15px; }
.question-edit-row label { width: 100%; color: var(--t2); }
.question-edit-row .el-input { flex: 1; min-width: 0; }
.questions-error { color: var(--el-color-danger); margin-bottom: 15px; font-size: var(--text-body); }
</style>
