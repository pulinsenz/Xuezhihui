<template>
  <div class="public-kb-page">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">公开知识广场</h2>
      </div>
      <div class="header-tools">
        <el-input
          v-model="keyword"
          placeholder="搜索知识库名称或关键词..."
          clearable
          class="search-input"
          aria-label="搜索公开知识库"
          :prefix-icon="Search"
          @keyup.enter="loadList()"
          @clear="loadList()"
        />
        <el-button :icon="Refresh" class="refresh-btn" @click="loadList()">刷新</el-button>
      </div>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="暂无符合条件的公开知识库" class="empty-card" />

    <!-- 弹性自适应卡片网格 -->
    <div v-else v-loading="loading" class="kb-grid">
      <div
        v-for="kb in list"
        :key="kb.id"
        class="kb-card"
        tabindex="0"
        @click="goDetail(kb.id)"
        @keydown.enter.prevent="goDetail(kb.id)"
      >
        <!-- 自己的公开知识库徽标 -->
        <div v-if="kb.isOwner" class="kb-badge-wrapper">
          <span class="badge-pill badge-mine">我的</span>
        </div>

        <KnowledgeCover :src="kb.cover" :name="kb.name" />

        <div class="kb-body">
          <div class="kb-head-row">
            <el-tooltip :content="kb.name" placement="top" :show-after="300">
              <h3 class="kb-name">{{ kb.name }}</h3>
            </el-tooltip>
            <el-tag size="small" type="success" effect="plain" class="public-tag">公开</el-tag>
          </div>

          <div class="kb-author">
            <el-avatar :size="20" class="author-avatar">{{ (kb.authorName || '?').charAt(0).toUpperCase() }}</el-avatar>
            <span class="author-name">{{ kb.authorName || '未知作者' }}</span>
          </div>

          <el-tooltip :content="kb.description || '暂无知识库简介'" placement="top" :show-after="300" :disabled="!kb.description">
            <p class="kb-desc">{{ kb.description || '暂无知识库简介' }}</p>
          </el-tooltip>

          <div class="kb-meta-row">
            <div class="kb-stats">
              <span class="stat"><el-icon><View /></el-icon>{{ kb.viewCount || 0 }}</span>
              <span class="stat"><el-icon><Star /></el-icon>{{ kb.favoriteCount || 0 }}</span>
            </div>
            <span class="doc-badge">{{ kb.docCount || 0 }} 篇文档</span>
          </div>

          <div class="kb-footer" @click.stop>
            <el-button v-if="kb.isOwner" size="small" type="primary" plain @click="goDetail(kb.id)">
              进入管理
            </el-button>
            <template v-else>
              <el-button
                v-if="kb.isFavorite"
                size="small"
                plain
                @click="handleUnfavorite(kb)"
              >
                已收藏
              </el-button>
              <el-button
                v-else
                size="small"
                type="warning"
                plain
                :icon="Star"
                @click="handleFavorite(kb)"
              >
                收藏
              </el-button>
              <el-button size="small" :icon="CopyDocument" @click="handleCopy(kb)">复制副本</el-button>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CopyDocument, Refresh, Search, Star, View } from '@element-plus/icons-vue'
import KnowledgeCover from '../components/KnowledgeCover.vue'
import { copyKnowledge, favoriteKnowledge, listPublicKnowledge, unfavoriteKnowledge } from '../api/knowledge'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const keyword = ref('')

const loadList = async () => {
  loading.value = true
  try {
    list.value = await listPublicKnowledge(keyword.value.trim() || undefined)
  } finally {
    loading.value = false
  }
}

const handleFavorite = async (kb) => {
  await favoriteKnowledge(kb.id)
  ElMessage.success('已收藏，可在「知识库」列表中查看')
  loadList()
}

const handleUnfavorite = async (kb) => {
  await unfavoriteKnowledge(kb.id)
  ElMessage.success('已取消收藏')
  loadList()
}

const handleCopy = async (kb) => {
  try {
    const newId = await copyKnowledge(kb.id)
    ElMessage.success('复制成功，你已成为副本作者，可自由编辑')
    router.push(`/knowledge/${newId}`)
  } catch (e) {
    // 错误拦截器已提示
  }
}

const goDetail = (id) => router.push(`/knowledge/${id}`)

onMounted(loadList)
</script>

<style scoped>
.public-kb-page {
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 1.6vw, 20px);
  width: 100%;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  flex-direction: column;
}

.page-title {
  margin: 0;
  font-size: var(--text-heading);
  font-weight: 700;
  color: var(--t1);
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.search-input {
  width: min(100%, clamp(220px, 20vw, 300px));
}

.search-input :deep(.el-input__wrapper) {
  border-radius: var(--r3);
}

.refresh-btn {
  border-radius: var(--r3);
}

.empty-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r1);
  padding: 48px 24px;
}

/* 核心自适应 Grid */
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

.badge-mine {
  background: var(--el-color-success);
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

.public-tag {
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
</style>
