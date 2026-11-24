<template>
  <div class="settings-page">
    <!-- 标题由顶栏承载；开关旁不再放状态 tag：开关本身就是状态，且 warning 黄色属于语义色挪用（uiup 组 17/19） -->
    <p class="group-title">文档入库</p>
    <section class="settings-card">
      <div class="setting-item">
        <div class="setting-main">
          <div class="setting-title-row">
            <span class="setting-title">知识库上传文档时默认入库</span>
          </div>
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
    </section>

    <p class="group-title">对话来源展示</p>
    <section class="settings-card">
      <div class="setting-item">
        <div class="setting-main">
          <div class="setting-title-row">
            <span class="setting-title">参考文献默认折叠</span>
          </div>
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
  gap: 10px;
  max-width: 960px;
}

/* 组 19：组标题 + 组间留白（10px 页内间距 + 标题自带下距）大于组内留白 */
.group-title {
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--t3);
  letter-spacing: 0.03em;
  margin: 10px 2px 0;
}

.settings-card {
  display: flex;
  flex-direction: column;
}

.setting-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 24px;
  background: var(--card);
  border-radius: var(--r1);
  box-shadow: var(--shadow-card);
  transition: box-shadow 0.2s ease-out;
}

.setting-item:hover {
  box-shadow: var(--shadow-card-hover);
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
  font-size: var(--text-heading);
  font-weight: 600;
  color: var(--t1);
}

.setting-control {
  padding-top: 2px;
  flex-shrink: 0;
}
</style>