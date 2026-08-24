<template>
  <div>
    <div class="page-header">
      <div>
        <h2>我的知识库</h2>
        <p class="sub">上传课程资料、校园文档，搭建你的专属知识库；收藏的他人知识库也会出现在这里</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建知识库</el-button>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="还没有知识库，点击右上角创建第一个" />

    <el-row v-loading="loading" :gutter="16" class="kb-row">
      <el-col v-for="kb in list" :key="kb.id" :span="8">
        <el-card shadow="hover" class="kb-card" @click="goDetail(kb.id)">
          <!-- 自己的库右上角“我的”；收藏的库显示“已收藏” -->
          <el-tag v-if="kb.isOwner" type="success" effect="dark" size="small" class="mine-badge">我的</el-tag>
          <el-tag v-else type="warning" effect="plain" size="small" class="mine-badge">已收藏</el-tag>

          <img v-if="kb.cover" :src="kb.cover" class="kb-cover-img" alt="封面" />
          <div v-else class="kb-cover">{{ kb.name.charAt(0) }}</div>

          <h3 class="kb-name">{{ kb.name }}</h3>
          <div class="kb-author">
            <el-avatar :size="18" class="author-avatar">{{ (kb.authorName || '?').charAt(0) }}</el-avatar>
            <span class="author-name">{{ kb.authorName || '未知用户' }}</span>
            <el-tag v-if="kb.isPublic === 1" size="small" type="success" effect="plain">公开</el-tag>
          </div>
          <p class="kb-desc">{{ kb.description || '暂无简介' }}</p>

          <div class="kb-stats">
            <span class="stat"><el-icon><View /></el-icon>{{ kb.viewCount || 0 }}</span>
            <span class="stat"><el-icon><Star /></el-icon>{{ kb.favoriteCount || 0 }}</span>
            <el-tag size="small" type="info">{{ kb.docCount }} 个文档</el-tag>
          </div>

          <div class="kb-footer" @click.stop>
            <template v-if="kb.isOwner">
              <el-button size="small" type="primary" plain :icon="Edit" @click="openEditDialog(kb)">编辑</el-button>
              <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(kb)">删除</el-button>
            </template>
            <el-button v-else size="small" type="warning" link @click="handleUnfavorite(kb)">取消收藏</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新建知识库弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建知识库" width="440px">
      <el-form :model="createForm" label-width="70px">
        <el-form-item label="名称" required>
          <el-input v-model="createForm.name" placeholder="如：数据结构课程资料" maxlength="128" />
        </el-form-item>
        <el-form-item label="封面">
          <el-upload :show-file-list="false" :http-request="handleCreateCoverUpload" accept="image/*">
            <img v-if="createForm.cover" :src="createForm.cover" class="cover-preview" alt="封面预览" />
            <div v-else class="cover-placeholder"><el-icon><Plus /></el-icon>上传封面</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可选" maxlength="512" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="createForm.isPublic" :active-value="1" :inactive-value="0" />
          <span class="switch-tip">公开后他人可浏览、收藏、复制</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑知识库弹窗（仅作者） -->
    <el-dialog v-model="editDialogVisible" title="编辑知识库" width="440px">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" placeholder="如：数据结构课程资料" maxlength="128" />
        </el-form-item>
        <el-form-item label="封面">
          <el-upload :show-file-list="false" :http-request="handleEditCoverUpload" accept="image/*">
            <img v-if="editForm.cover" :src="editForm.cover" class="cover-preview" alt="封面预览" />
            <div v-else class="cover-placeholder"><el-icon><Plus /></el-icon>上传封面</div>
          </el-upload>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="可选" maxlength="512" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="editForm.isPublic" :active-value="1" :inactive-value="0" />
          <span class="switch-tip">公开后他人可浏览、收藏、复制</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="handleEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Star, View } from '@element-plus/icons-vue'
import {
  createKnowledge,
  deleteKnowledge,
  listMyKnowledge,
  unfavoriteKnowledge,
  updateKnowledge,
  uploadCover,
} from '../api/knowledge'

const router = useRouter()
const list = ref([])
const loading = ref(false)

const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', description: '', cover: '', isPublic: 0 })

const editDialogVisible = ref(false)
const editing = ref(false)
const editForm = reactive({ id: null, name: '', description: '', cover: '', isPublic: 0 })

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
  createForm.cover = ''
  createForm.isPublic = 0
  createDialogVisible.value = true
}

const handleCreate = async () => {
  if (!createForm.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  creating.value = true
  try {
    await createKnowledge({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
      cover: createForm.cover || undefined,
      isPublic: createForm.isPublic,
    })
    ElMessage.success('创建成功')
    createDialogVisible.value = false
    loadList()
  } finally {
    creating.value = false
  }
}

const openEditDialog = (kb) => {
  editForm.id = kb.id
  editForm.name = kb.name
  editForm.description = kb.description || ''
  editForm.cover = kb.cover || ''
  editForm.isPublic = kb.isPublic === 1 ? 1 : 0
  editDialogVisible.value = true
}

const handleEdit = async () => {
  if (!editForm.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  editing.value = true
  try {
    await updateKnowledge({
      id: editForm.id,
      name: editForm.name.trim(),
      description: editForm.description.trim() || undefined,
      cover: editForm.cover || undefined,
      isPublic: editForm.isPublic,
    })
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    loadList()
  } finally {
    editing.value = false
  }
}

// 封面上传：返回 /api/files/... 的可访问 URL
const handleCreateCoverUpload = async (options) => {
  const url = await uploadCover(options.file)
  createForm.cover = url
  ElMessage.success('封面上传成功')
}
const handleEditCoverUpload = async (options) => {
  const url = await uploadCover(options.file)
  editForm.cover = url
  ElMessage.success('封面上传成功')
}

const handleUnfavorite = async (kb) => {
  await unfavoriteKnowledge(kb.id)
  ElMessage.success('已取消收藏')
  loadList()
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
  position: relative;
}
.mine-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
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
.kb-cover-img {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 6px;
  margin-bottom: 12px;
  display: block;
}
.kb-name {
  font-size: 16px;
  color: #303133;
}
.kb-author {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}
.author-avatar {
  background: #909399;
  color: #fff;
  font-size: 11px;
  flex-shrink: 0;
}
.author-name {
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.kb-stats {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #909399;
  font-size: 12px;
}
.kb-stats .stat {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.kb-footer {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}
.cover-preview {
  width: 180px;
  height: 90px;
  object-fit: cover;
  border-radius: 6px;
  display: block;
}
.cover-placeholder {
  width: 180px;
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: #909399;
  font-size: 13px;
  cursor: pointer;
}
.switch-tip {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
