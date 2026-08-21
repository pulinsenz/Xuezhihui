<template>
  <div>
    <div class="page-header">
      <div>
        <h2>我的知识库</h2>
        <p class="sub">上传课程资料、校园文档，搭建你的专属知识库</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建知识库</el-button>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="还没有知识库，点击右上角创建第一个" />

    <el-row v-loading="loading" :gutter="16" class="kb-row">
      <el-col v-for="kb in list" :key="kb.id" :span="8">
        <el-card shadow="hover" class="kb-card" @click="goDetail(kb.id)">
          <div class="kb-cover">{{ kb.name.charAt(0) }}</div>
          <h3 class="kb-name">{{ kb.name }}</h3>
          <p class="kb-desc">{{ kb.description || '暂无简介' }}</p>
          <div class="kb-footer">
            <el-tag size="small" type="info">{{ kb.docCount }} 个文档</el-tag>
            <el-button size="small" type="danger" link :icon="Delete" @click.stop="handleDelete(kb)">删除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新建知识库弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建知识库" width="420px">
      <el-form :model="createForm" label-width="70px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="如：数据结构课程资料" maxlength="128" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可选" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { listMyKnowledge, createKnowledge, deleteKnowledge } from '../api/knowledge'

const router = useRouter()
const list = ref([])
const loading = ref(false)

const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', description: '' })

const loadList = async () => {
  loading.value = true
  try {
    list.value = await listMyKnowledge()
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  createForm.name = ''
  createForm.description = ''
  createDialogVisible.value = true
}

const handleCreate = async () => {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  creating.value = true
  try {
    await createKnowledge({ name: createForm.name.trim(), description: createForm.description.trim() || undefined })
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    loadList()
  } finally {
    creating.value = false
  }
}

const handleDelete = async (kb) => {
  await ElMessageBox.confirm(`确定删除知识库「${kb.name}」吗？其下文档会一并删除。`, '删除确认', {
    type: 'warning',
  })
  await deleteKnowledge(kb.id)
  ElMessage.success('删除成功')
  loadList()
}

const goDetail = (id) => router.push(`/knowledge/${id}`)

onMounted(loadList)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.page-header h2 {
  font-size: 20px;
}
.sub {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.kb-row {
  margin: 0 -8px;
}
.kb-card {
  margin-bottom: 16px;
  cursor: pointer;
}
.kb-cover {
  height: 90px;
  line-height: 90px;
  text-align: center;
  font-size: 40px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  border-radius: 6px;
  margin-bottom: 12px;
}
.kb-name {
  font-size: 16px;
  color: #303133;
}
.kb-desc {
  margin-top: 6px;
  min-height: 20px;
  font-size: 13px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.kb-footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
