<template>
  <div class="public-kb-page">
    <div class="page-header">
      <!-- 标题由顶栏承载，此处只保留搜索与刷新工具（去AI味：删同屏重复标题） -->
      <div class="header-tools">
        <el-input
          v-model="keyword"
          placeholder="搜索知识库名称..."
          clearable
          class="search-input"
          :prefix-icon="Search"
          @keyup.enter="loadList()"
          @clear="loadList()"
        />
        <el-button :icon="Refresh" @click="loadList()">刷新</el-button>
      </div>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="暂无符合条件的公开知识库" />

    <el-row v-loading="loading" :gutter="20" class="kb-row">
      <el-col v-for="kb in list" :key="kb.id" :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
        <div
          class="kb-card"
          tabindex="0"
          @click="goDetail(kb.id)"
          @keydown.enter.prevent="goDetail(kb.id)"
        >
          <!-- 自己的公开知识库：右上角“我的”徽标 -->
          <div v-if="kb.isOwner" class="kb-badge-wrapper">
            <el-tag type="success" effect="dark" size="small" class="mine-badge">我的</el-tag>
          </div>

          <div class="cover-box">
            <img v-if="kb.cover" :src="kb.cover" class="kb-cover-img" alt="封面" />
            <div v-else class="kb-cover">{{ kb.name.charAt(0) }}</div>
          </div>

          <div class="kb-body">
            <div class="kb-head-row">
              <el-tooltip :content="kb.name" placement="top" :show-after="300">
                <h3 class="kb-name">{{ kb.name }}</h3>
              </el-tooltip>
              <el-tag size="small" type="success" effect="plain">公开</el-tag>
            </div>

            <div class="kb-author">
              <el-avatar :size="20" class="author-avatar">{{ (kb.authorName || '?').charAt(0) }}</el-avatar>
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
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CopyDocument, Refresh, Search, Star, View } from '@element-plus/icons-vue'
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
  gap: 20px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.search-input {
  width: 260px;
}

.kb-row {
  margin: 0 -10px;
}

.kb-card {
  position: relative;
  background: #ffffff;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 16px;
  overflow: hidden;
  margin-bottom: 20px;
  cursor: pointer;
  box-shadow: var(--shadow-card);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
}

.kb-card:hover {
  border-color: rgba(15, 118, 110, 0.4);
}

.kb-card:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
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
  font-size: 14px;
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
  background: rgba(15, 118, 110, 0.08);
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

.kb-footer :deep(.el-button) {
  min-height: 28px;
}
</style>