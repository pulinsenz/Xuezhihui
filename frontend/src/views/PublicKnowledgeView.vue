<template>
  <div>
    <div class="page-header">
      <div>
        <h2>公开知识库</h2>
        <p class="sub">发现他人分享的知识库，可收藏进自己的列表，也可一键复制</p>
      </div>
      <div class="header-tools">
        <el-input
          v-model="keyword"
          placeholder="搜索知识库名称"
          clearable
          style="width: 240px"
          :prefix-icon="Search"
          @keyup.enter="loadList()"
          @clear="loadList()"
        />
        <el-button :icon="Refresh" @click="loadList()">刷新</el-button>
      </div>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="暂无公开知识库" />

    <el-row v-loading="loading" :gutter="16" class="kb-row">
      <el-col v-for="kb in list" :key="kb.id" :xs="24" :sm="12" :md="8" :lg="6" :xl="6">
        <el-card shadow="hover" class="kb-card" @click="goDetail(kb.id)">
          <!-- 自己的公开知识库：右上角“我的”徽标 -->
          <el-tag v-if="kb.isOwner" type="success" effect="dark" size="small" class="mine-badge">我的</el-tag>
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
            <!-- 自己的库：进入编辑 -->
            <el-button v-if="kb.isOwner" size="small" type="primary" @click="goDetail(kb.id)">进入</el-button>
            <template v-else>
              <el-button
                v-if="kb.isFavorite"
                size="small"
                plain
                @click="handleUnfavorite(kb)"
              >已收藏</el-button>
              <el-button
                v-else
                size="small"
                type="warning"
                plain
                @click="handleFavorite(kb)"
              >收藏</el-button>
              <el-button size="small" :icon="CopyDocument" @click="handleCopy(kb)">复制</el-button>
            </template>
          </div>
        </el-card>
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
    // 错误已由拦截器提示
  }
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
.header-tools {
  display: flex;
  align-items: center;
  gap: 8px;
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
  height: 180px;
  line-height: 180px;
  text-align: center;
  font-size: 48px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  border-radius: 6px;
  margin-bottom: 12px;
}
.kb-cover-img {
  width: 100%;
  height: 180px;
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
</style>
