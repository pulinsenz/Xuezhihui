<template>
  <div class="kb-list-page">
    <div class="page-header">
      <!-- 标题由顶栏承载；空态时隐藏右上按钮，避免与空态"立即创建"同屏双主按钮（uiup 组 14） -->
      <el-button
        v-if="loading || list.length > 0"
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
    >
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">立即创建</el-button>
    </el-empty>

    <el-row v-loading="loading" :gutter="20" class="kb-row">
      <el-col v-for="kb in list" :key="kb.id" :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
        <div class="kb-card" @click="goDetail(kb.id)">
          <!-- 身份角标 -->
          <div class="kb-badge-wrapper">
            <el-tag v-if="kb.isOwner" type="success" effect="dark" size="small" class="mine-badge">我的</el-tag>
            <el-tag v-else-if="kb.isMember" type="primary" effect="dark" size="small" class="mine-badge">协作中</el-tag>
            <el-tag v-else type="warning" effect="dark" size="small" class="mine-badge">已收藏</el-tag>
          </div>

          <!-- 封面图/渐变首字 -->
          <div class="cover-box">
            <img v-if="kb.cover" :src="kb.cover" class="kb-cover-img" alt="封面" />
            <div v-else class="kb-cover">{{ kb.name.charAt(0) }}</div>
          </div>

          <div class="kb-body">
            <div class="kb-head-row">
              <h3 class="kb-name" :title="kb.name">{{ kb.name }}</h3>
              <el-tag v-if="kb.isPublic === 1" size="small" type="success" effect="plain">公开</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">私有</el-tag>
            </div>

            <div class="kb-author">
              <el-avatar :size="20" class="author-avatar">{{ (kb.authorName || '?').charAt(0) }}</el-avatar>
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
      </el-col>
    </el-row>

    <!-- 新建知识库弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建知识库" width="460px" class="custom-dialog">
      <el-form :model="createForm" label-position="top">
        <el-form-item label="知识库名称" required>
          <el-input v-model="createForm.name" placeholder="如：计算机体系结构课程资料" maxlength="128" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-upload :show-file-list="false" :http-request="handleCreateCoverUpload" accept="image/*">
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
        <el-button type="primary" :loading="creating" @click="handleCreate">创建知识库</el-button>
      </template>
    </el-dialog>

    <!-- 编辑知识库弹窗（仅作者） -->
    <el-dialog v-model="editDialogVisible" title="编辑知识库" width="460px" class="custom-dialog">
      <el-form :model="editForm" label-position="top">
        <el-form-item label="知识库名称" required>
          <el-input v-model="editForm.name" placeholder="如：计算机体系结构课程资料" maxlength="128" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-upload :show-file-list="false" :http-request="handleEditCoverUpload" accept="image/*">
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
        <el-button type="primary" :loading="editing" @click="handleEdit">保存变更</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
const route = useRoute()
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

// 仪表盘"新建知识库"磁贴经 ?new=1 直达：自动弹新建框并清掉 query，刷新后不重复弹
onMounted(() => {
  loadList()
  if (route.query.new) {
    openCreateDialog()
    router.replace({ path: '/knowledge' })
  }
})
</script>

<style scoped>
.kb-list-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
}

.create-btn {
  border-radius: 12px;
  font-weight: 600;
}

.kb-row {
  margin: 0 -10px;
}

/* 知识库卡片 */
.kb-card {
  position: relative;
  background: #ffffff;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: var(--r1);
  overflow: hidden;
  margin-bottom: 20px;
  cursor: pointer;
  box-shadow: var(--shadow-card);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
  display: flex;
  flex-direction: column;
}

.kb-card:hover {
  border-color: rgba(20, 184, 166, 0.4);
}

.kb-badge-wrapper {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
}

.cover-box {
  height: 140px;
  width: 100%;
  overflow: hidden;
  background: #f1f5f9;
}

.kb-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.kb-cover {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  font-weight: 700;
  color: #ffffff;
  background: var(--brand);
}

.kb-body {
  padding: 16px 18px;
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
  font-size: 16px;
  font-weight: 700;
  color: var(--t1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.kb-author {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

.author-avatar {
  background: var(--t3);
  color: #fff;
  font-size: 12px;
  flex-shrink: 0;
}

.author-name {
  font-size: 12px;
  color: var(--t3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kb-desc {
  margin-top: 8px;
  min-height: 36px;
  font-size: 12px;
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
  border-top: 1px solid rgba(241, 245, 249, 0.9);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kb-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--t4);
  font-size: 12px;
}

.kb-stats .stat {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.doc-badge {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand);
  background: rgba(20, 184, 166, 0.1);
  padding: 2px 8px;
  border-radius: 6px;
}

.kb-footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.cover-preview {
  width: 100%;
  height: 110px;
  object-fit: cover;
  border-radius: 12px;
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
  border: 1px dashed rgba(203, 213, 225, 0.8);
  border-radius: 12px;
  color: var(--t3);
  font-size: 12px;
  cursor: pointer;
  background: var(--bg);
  transition: all 0.2s ease;
}

.cover-placeholder:hover {
  border-color: #14b8a6;
  color: var(--brand);
  background: rgba(20, 184, 166, 0.04);
}

.visibility-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.visibility-tip {
  font-size: 12px;
  color: var(--t3);
}
</style>