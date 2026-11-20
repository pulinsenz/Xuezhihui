<script setup>
import { ICONS } from '../icons'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 15 },
})
</script>

<template>
  <!-- 行内小图标相对文字下移约 1% 字高，视觉垂直居中 -->
  <svg
    :width="size" :height="size" viewBox="0 0 1024 1024"
    style="fill: currentColor; flex: none; vertical-align: -0.12em" aria-hidden="true"
  >
    <path v-for="(d, i) in ICONS[props.name] || []" :key="i" :d="d" />
  </svg>
</template>
