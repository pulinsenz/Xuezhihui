<template>
  <div>
    <div class="page-header">
      <h2>设置</h2>
      <p class="sub">个性化设置</p>
    </div>

    <el-card shadow="never">
      <div class="setting-row">
        <div class="setting-info">
          <div class="setting-title">知识库上传文档时默认入库</div>
          <div class="setting-desc">
            开启：上传文档后自动向量化，可被对话检索；<br />
            关闭：新上传文档只保留记录、不向量化（状态为"未入库"），可在文档列表手动「重新入库」。
          </div>
        </div>
        <el-switch
          v-model="defaultVectorize"
          :active-value="1"
          :inactive-value="0"
          :loading="saving"
          @change="handleChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSettings, updateVectorizeDefault } from '../api/settings'

const defaultVectorize = ref(1)
const saving = ref(false)

onMounted(async () => {
  try {
    const data = await getSettings()
    defaultVectorize.value = data.defaultVectorize ?? 1
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
    // 保存失败回滚开关
    defaultVectorize.value = val === 1 ? 0 : 1
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page-header {
  margin-bottom: 16px;
}
.page-header h2 {
  font-size: 20px;
}
.sub {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 8px 0;
}
.setting-title {
  font-size: 15px;
  color: #303133;
}
.setting-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
  line-height: 1.7;
}
</style>
