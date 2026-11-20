<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import XIcon from './XIcon.vue'

/**
 * 轻提示：两秒自消（成功对勾 2.5s）。
 * 命令式调用：import { toast } from '../components/toast'; toast.success('已保存')
 * 原理：toast 模块向 window 派发 x-toast 事件，本常驻组件（App.vue 挂载）监听并渲染。
 */
const items = ref([])
let seq = 0
let handler = null

onMounted(() => {
  handler = (e) => {
    const { text, kind } = e.detail
    const id = ++seq
    items.value.push({ id, text, kind })
    setTimeout(() => {
      items.value = items.value.filter((x) => x.id !== id)
    }, kind === 'ok' ? 2500 : 2000)
  }
  window.addEventListener('x-toast', handler)
})
onBeforeUnmount(() => window.removeEventListener('x-toast', handler))
</script>

<template>
  <Teleport to="body">
    <div class="toast-wrap">
      <div v-for="t in items" :key="t.id" class="toast" :class="t.kind">
        <XIcon class="t-ic" :name="t.kind === 'ok' ? 'circleCheckFilled' : 'warningFilled'" :size="14" />
        {{ t.text }}
      </div>
    </div>
  </Teleport>
</template>
