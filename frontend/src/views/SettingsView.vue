<template>
  <div class="settings-page">
    <!-- 标题由顶栏承载；开关旁不再放状态 tag：开关本身就是状态，且 warning 黄色属于语义色挪用（uiup 组 17/19） -->
    <section class="settings-card">
      <div class="setting-item">
        <div class="setting-main">
          <div class="setting-title-row">
            <span class="setting-title">知识库上传文档时默认入库</span>
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
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
  transition: border-color 0.2s ease;
}

.setting-item:hover {
  border-color: rgba(20, 184, 166, 0.28);
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
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.setting-control {
  padding-top: 2px;
  flex-shrink: 0;
}
</style>