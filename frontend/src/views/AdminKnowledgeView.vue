<template>
  <div>
    <div class="page-header">
      <div>
        <h2>全局知识库</h2>
        <p class="sub">查看所有用户的知识库与文档统计</p>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索知识库名称" clearable style="width: 260px" :prefix-icon="Search" @keyup.enter="loadData(1)" @clear="loadData(1)" />
        <el-button type="primary" :icon="Search" @click="loadData(1)">查询</el-button>
      </div>

      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="name" label="知识库名称" min-width="180" />
        <el-table-column prop="userId" label="所属用户 ID" width="200" />
        <el-table-column prop="docCount" label="文档数" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.docCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ').slice(0, 19) }}</template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @current-change="loadData()"
        @size-change="loadData(1)"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { listAllKnowledge } from '../api/admin'

const keyword = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const loadData = async (page) => {
  if (page) pageNum.value = page
  loading.value = true
  try {
    const data = await listAllKnowledge({ keyword: keyword.value.trim() || undefined, pageNum: pageNum.value, pageSize: pageSize.value })
    list.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
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
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
