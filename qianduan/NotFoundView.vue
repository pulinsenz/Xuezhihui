<template>
  <el-empty description="页面不存在">
    <el-button type="primary" @click="router.push('/knowledge')">返回首页</el-button>
  </el-empty>
</template>

<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>
