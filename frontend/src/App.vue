<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
// Element Plus 按需模式下中文语言包经 ConfigProvider 下发
// （替代全量安装时的 app.use(ElementPlus, { locale })），组件本体由 resolver 按需引入
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>
