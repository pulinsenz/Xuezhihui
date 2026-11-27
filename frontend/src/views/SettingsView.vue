<template>
  <div class="settings-page">
    <header class="settings-head">
      <h2 class="head-title">系统偏好设置</h2>
    </header>

    <!-- 设置组：文档入库 -->
    <section class="settings-group">
      <h3 class="group-title">文档与向量检索配置</h3>
      <div class="settings-card">
        <div class="setting-item">
          <div class="setting-main">
            <span class="setting-title">知识库上传文档时默认自动向量化入库</span>
            <p class="setting-desc">开启后，上传 TXT、PDF、Markdown 等文档将自动提交切片并建立向量索引，以便即时参与 RAG 对话问答。</p>
          </div>
          <div class="setting-control">
            <el-switch
              v-model="defaultVectorize"
              aria-label="上传文档时默认入库"
              :active-value="1"
              :inactive-value="0"
              :loading="saving"
              inline-prompt
              active-text="开启"
              inactive-text="关闭"
              @change="handleChange"
            />
          </div>
        </div>
      </div>
    </section>

    <!-- 设置组：智能对话界面呈现 -->
    <section class="settings-group">
      <h3 class="group-title">智能对话呈现偏好</h3>
      <div class="settings-card">
        <div class="setting-item">
          <div class="setting-main">
            <span class="setting-title">参考文献与知识溯源默认收起折叠</span>
            <p class="setting-desc">开启后，AI 助手在每次回答末尾附带的文档溯源卡片将默认处于折叠状态，保持对话流清爽，点击可随时展开。</p>
          </div>
          <div class="setting-control">
            <el-switch
              v-model="collapseRefs"
              aria-label="默认折叠回答的引用"
              :active-value="1"
              :inactive-value="0"
              :loading="saving"
              inline-prompt
              active-text="折叠"
              inactive-text="展开"
              @change="handleCollapseChange"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, updateCollapseRefs, updateVectorizeDefault } from '../api/settings'

const defaultVectorize = ref(1)
const collapseRefs = ref(1)
const saving = ref(false)

onMounted(async () => {
  try {
    const data = await getSettings()
    defaultVectorize.value = data.defaultVectorize ?? 1
    collapseRefs.value = data.collapseRefs ?? 1
  } catch (e) {
    // 错误已由拦截器提示
  }
})

const handleChange = async (val) => {
  saving.value = true
  try {
    await updateVectorizeDefault(val)
    ElMessage.success(val === 1 ? '已开启默认入库' : '已关闭默认入库，新上传文档将不入库')
  } catch (e) {
    defaultVectorize.value = val === 1 ? 0 : 1
  } finally {
    saving.value = false
  }
}

const handleCollapseChange = async (val) => {
  saving.value = true
  try {
    await updateCollapseRefs(val)
    ElMessage.success(val === 1 ? '参考文献默认折叠' : '参考文献默认展开')
  } catch (e) {
    collapseRefs.value = val === 1 ? 0 : 1
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.settings-page {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.8vw, 24px);
  width: 100%;
}

.settings-head {
  display: flex;
  flex-direction: column;
}

.head-title {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.settings-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.group-title {
  margin: 0;
  font-size: var(--text-body);
  font-weight: 700;
  color: var(--brand);
  letter-spacing: 0.02em;
}

.settings-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: clamp(16px, 1.8vw, 22px);
  transition: background 0.15s ease;
}

.setting-item:hover {
  background: var(--bg);
}

.setting-main {
  flex: 1;
  min-width: 0;
}

.setting-title {
  font-size: var(--text-body);
  font-weight: 600;
  color: var(--t1);
}

.setting-desc {
  margin: 6px 0 0;
  font-size: var(--text-body);
  color: var(--t3);
  line-height: 1.5;
}

.setting-control {
  flex-shrink: 0;
}
</style>
