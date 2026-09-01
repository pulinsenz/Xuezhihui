<template>
  <div class="settings-page">
    <section class="hero-bar">
      <div>
        <p class="hero-kicker">系统偏好</p>
        <h2>个性化与工作区设置</h2>
        <p class="hero-subtitle">自定义文档入库流程与 AI 对话引用展示规则</p>
      </div>
    </section>

    <section class="settings-card">
      <div class="setting-item">
        <div class="setting-main">
          <div class="setting-title-row">
            <span class="setting-title">知识库上传文档时默认入库</span>
            <el-tag :type="defaultVectorize === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ defaultVectorize === 1 ? '自动向量化' : '手动入库' }}
            </el-tag>
          </div>
          <p class="setting-desc">
            开启后新上传的文档将自动切块入库并建立向量索引，立即可供 AI 对话检索；关闭后仅保存文档元数据，可随时在文档列表按需入库。
          </p>
        </div>
        <div class="setting-control">
          <el-switch
            v-model="defaultVectorize"
            :active-value="1"
            :inactive-value="0"
            :loading="saving"
            inline-prompt
            active-text="开"
            inactive-text="关"
            @change="handleChange"
          />
        </div>
      </div>

      <div class="setting-item">
        <div class="setting-main">
          <div class="setting-title-row">
            <span class="setting-title">参考文献默认折叠</span>
            <el-tag :type="collapseRefs === 1 ? 'warning' : 'success'" size="small" effect="plain">
              {{ collapseRefs === 1 ? '默认收起' : '默认展开' }}
            </el-tag>
          </div>
          <p class="setting-desc">
            开启后 AI 对话回复底部的知识库检索切片和参考来源将默认收起，保持版面清爽利落；关闭后将直接平铺展示所有溯源来源。
          </p>
        </div>
        <div class="setting-control">
          <el-switch
            v-model="collapseRefs"
            :active-value="1"
            :inactive-value="0"
            :loading="saving"
            inline-prompt
            active-text="开"
            inactive-text="关"
            @change="handleCollapseChange"
          />
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
  gap: 20px;
  max-width: 960px;
}

.hero-bar {
  padding: 4px 2px;
}

.hero-kicker {
  font-size: 13px;
  color: #0f766e;
  font-weight: 600;
}

.hero-bar h2 {
  margin: 4px 0 0;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.hero-subtitle {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.settings-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.setting-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px;
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
  backdrop-filter: blur(12px);
  transition: all 0.2s ease;
}

.setting-item:hover {
  border-color: rgba(20, 184, 166, 0.28);
  box-shadow: 0 14px 34px rgba(20, 184, 166, 0.06);
}

.setting-main {
  flex: 1;
  min-width: 0;
}

.setting-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.setting-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.setting-desc {
  margin-top: 6px;
  font-size: 13.5px;
  color: #64748b;
  line-height: 1.6;
}

.setting-control {
  padding-top: 2px;
  flex-shrink: 0;
}
</style>