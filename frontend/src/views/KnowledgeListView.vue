<template>
  <div class="kb-list-page">
    <!-- 空态时隐藏右上按钮，避免与空态"立即创建"同屏双主按钮 -->
    <div v-if="loading || list.length > 0" class="page-header">
      <el-button
        type="primary"
        :icon="Plus"
        class="create-btn"
        @click="openCreateDialog"
      >
        新建知识库
      </el-button>
    </div>

    <el-empty
      v-if="!loading && list.length === 0"
      description="还没有知识库，点击下方开启你的第一个知识库吧"
      class="empty-card"
    >
      <el-button type="primary" :icon="Plus" class="create-btn" @click="openCreateDialog">立即创建</el-button>
    </el-empty>

    <!-- 弹性自适应 Grid 栅格：彻底消除浏览器缩放下的卡片挤压与不对齐 -->
    <div v-else v-loading="loading" class="kb-grid">
      <div
        v-for="kb in list"
        :key="kb.id"
        class="kb-card"
        tabindex="0"
        @click="goDetail(kb.id)"
        @keydown.enter.prevent="goDetail(kb.id)"
      >
        <!-- 身份角标 -->
        <div class="kb-badge-wrapper">
          <span v-if="kb.isOwner" class="badge-pill badge-owner">我的</span>
          <span v-else-if="kb.isMember" class="badge-pill badge-collab">协作中</span>
          <span v-else class="badge-pill badge-fav">已收藏</span>
        </div>

        <KnowledgeCover :src="kb.cover" :name="kb.name" />

        <div class="kb-body">
          <div class="kb-head-row">
            <h3 class="kb-name" :title="kb.name">{{ kb.name }}</h3>
            <el-tag v-if="kb.isPublic === 1" size="small" type="success" effect="plain" class="status-tag">公开</el-tag>
            <el-tag v-else size="small" type="info" effect="plain" class="status-tag">私有</el-tag>
          </div>

          <div class="kb-author">
            <el-avatar :size="20" class="author-avatar">{{ (kb.authorName || '?').charAt(0).toUpperCase() }}</el-avatar>
            <span class="author-name">{{ kb.authorName || '未知作者' }}</span>
          </div>

          <p class="kb-desc" :title="kb.description">{{ kb.description || '暂无知识库简介描述' }}</p>

          <div class="kb-meta-row">
            <div class="kb-stats">
              <span class="stat"><el-icon><View /></el-icon>{{ kb.viewCount || 0 }}</span>
              <span class="stat"><el-icon><Star /></el-icon>{{ kb.favoriteCount || 0 }}</span>
            </div>
            <span class="doc-badge">{{ kb.docCount || 0 }} 篇文档</span>
          </div>

          <div class="kb-footer" @click.stop>
            <template v-if="kb.isOwner">
              <el-button size="small" plain :icon="Edit" @click="openEditDialog(kb)">编辑</el-button>
              <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(kb)">删除</el-button>
            </template>
            <el-button v-else-if="kb.isMember" size="small" type="primary" link @click="goDetail(kb.id)">进入管理</el-button>
            <el-button v-else size="small" type="warning" link @click="handleUnfavorite(kb)">取消收藏</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建知识库弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建知识库" width="min(480px, calc(100vw - 32px))" class="custom-dialog" :close-on-click-modal="false">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="知识库名称" required>
          <el-input v-model="createForm.name" placeholder="如：计算机体系结构课程资料" maxlength="128" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-upload :show-file-list="false" :http-request="handleCreateCoverUpload" accept="image/*" class="cover-uploader">
            <img v-if="createForm.cover" :src="createForm.cover" class="cover-preview" alt="封面预览" />
            <div v-else class="cover-placeholder">
              <el-icon><Plus /></el-icon>
              <span>上传知识库封面</span>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="知识库简介">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="简要介绍该知识库包含哪些内容..." maxlength="512" />
        </el-form-item>
        <el-form-item label="可见性">
          <div class="visibility-row">
            <el-radio-group v-model="createForm.isPublic">
              <el-radio :value="0">私有</el-radio>
              <el-radio :value="1">公开</el-radio>
            </el-radio-group>
            <span class="visibility-tip">公开后所有人可在公开知识库中检索与浏览</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" class="btn-brand" @click="handleCreate">创建知识库</el-button>
      </template>
    </el-dialog>

    <!-- 编辑知识库弹窗（仅作者） -->
    <el-dialog v-model="editDialogVisible" title="编辑知识库" width="min(480px, calc(100vw - 32px))" class="custom-dialog" :close-on-click-modal="false">
      <el-form :model="editForm" label-position="top">
        <el-form-item label="知识库名称" required>
          <el-input v-model="editForm.name" placeholder="如：计算机体系结构课程资料" maxlength="128" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-upload :show-file-list="false" :http-request="handleEditCoverUpload" accept="image/*" class="cover-uploader">
            <img v-if="editForm.cover" :src="editForm.cover" class="cover-preview" alt="封面预览" />
            <div v-else class="cover-placeholder">
              <el-icon><Plus /></el-icon>
              <span>上传知识库封面</span>
            </div>
          </el-upload>
        </el-form-item>
        <el-form-item label="知识库简介">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="简要介绍该知识库包含哪些内容..." maxlength="512" />
        </el-form-item>
        <el-form-item label="可见性">
          <div class="visibility-row">
            <el-radio-group v-model="editForm.isPublic">
              <el-radio :value="0">私有</el-radio>
              <el-radio :value="1">公开</el-radio>
            </el-radio-group>
            <span class="visibility-tip">公开后所有人可在公开知识库中检索与浏览</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" class="btn-brand" @click="handleEdit">保存变更</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Star, View } from '@element-plus/icons-vue'
import KnowledgeCover from '../components/KnowledgeCover.vue'
import {
  createKnowledge,
  deleteKnowledge,
  listMyKnowledge,
  unfavoriteKnowledge,
  updateKnowledge,
  uploadCover,
} from '../api/knowledge'
import { useTabRefresh } from '../utils/tabRefresh'

const router = useRouter()
const route = useRoute()
const list = ref([])
const loading = ref(false)

const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive({ name: '', description: '', cover: '', isPublic: 0 })

const editDialogVisible = ref(false)
const editing = ref(false)
const editForm = reactive({ id: null, name: '', description: '', cover: '', isPublic: 0 })

const loadList = useTabRefresh(async (quiet, isCurrent) => {
  loading.value = !quiet
  try {
    const result = await listMyKnowledge()
    if (isCurrent()) list.value = result
  } finally {
    if (isCurrent()) loading.value = false
  }
}, ['list'])

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
onDeactivated(() => {
  createDialogVisible.value = false
  editDialogVisible.value = false
})

// 仪表盘的快捷入口也可能进入已经挂载的知识库页。
watch(
  () => route.name === 'KnowledgeList' && route.query.new,
  (open) => {
    if (!open) return
    openCreateDialog()
    router.replace({ path: '/knowledge' })
  },
  { immediate: true },
)
</script>

<style scoped>
.kb-list-page {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.6vw, 20px);
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: flex-end;
}

.create-btn {
  border-radius: var(--r3);
  background: var(--brand);
  border-color: var(--brand);
  font-weight: 600;
  min-height: 38px;
  box-shadow: 0 2px 6px var(--tint-strong);
}

.create-btn:hover {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
}

.empty-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: 48px 24px;
}

/* 核心抗缩放自适应网格 */
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, clamp(260px, 22vw, 320px)), 1fr));
  gap: clamp(14px, 1.5vw, 20px);
}

.kb-card {
  position: relative;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-card);
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  display: flex;
  flex-direction: column;
}

.kb-card:hover {
  border-color: var(--tint-strong);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.kb-card:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.kb-badge-wrapper {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
}

.badge-pill {
  font-size: var(--text-muted);
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--r-pill);
  box-shadow: 0 1px 3px color-mix(in srgb, var(--t1) 12%, transparent);
}

.badge-owner {
  background: var(--el-color-success);
  color: var(--card);
}

.badge-collab {
  background: var(--brand);
  color: var(--card);
}

.badge-fav {
  background: var(--el-color-warning);
  color: var(--card);
}

.kb-body {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.kb-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.kb-name {
  margin: 0;
  font-size: var(--text-body);
  font-weight: 700;
  color: var(--t1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.status-tag {
  flex-shrink: 0;
}

.kb-author {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.author-avatar {
  background: var(--t3);
  color: var(--card);
  font-size: var(--text-muted);
  flex-shrink: 0;
}

.author-name {
  font-size: var(--text-muted);
  color: var(--t3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-desc {
  margin: 8px 0 0;
  min-height: 36px;
  font-size: var(--text-body);
  color: var(--t3);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.kb-meta-row {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--t4);
  font-size: var(--text-muted);
}

.kb-stats .stat {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.doc-badge {
  font-size: var(--text-muted);
  font-weight: 600;
  color: var(--brand);
  background: var(--tint);
  border: 1px solid var(--tint-strong);
  padding: 1px 7px;
  border-radius: var(--r3);
}

.kb-footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.kb-footer :deep(.el-button) {
  margin-inline-start: 0;
  min-height: 28px;
  border-radius: var(--r3);
}

.cover-uploader {
  width: 100%;
}

.cover-preview {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: var(--r3);
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 90px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px dashed var(--line);
  border-radius: var(--r3);
  color: var(--t3);
  font-size: var(--text-muted);
  cursor: pointer;
  background: var(--bg);
  transition: all 0.2s ease;
}

.cover-placeholder:hover {
  border-color: var(--brand);
  color: var(--brand);
  background: var(--tint);
}

.visibility-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.visibility-tip {
  font-size: var(--text-muted);
  color: var(--t3);
}

.btn-brand {
  background: var(--brand);
  border-color: var(--brand);
}
</style>
