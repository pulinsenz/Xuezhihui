<script setup>
import XButton from './XButton.vue'

defineProps({
  title: { type: String, default: '' },
  sub: { type: String, default: '' },
  actionText: { type: String, default: '' },
})
const emit = defineEmits(['action'])
</script>

<template>
  <div class="x-empty">
    <!-- 空态插画：六边形节点虚线环（圆点×线母题） -->
    <svg viewBox="0 0 220 120" width="200" height="110" aria-hidden="true">
      <path d="M30 60 L80 30 L150 40 L190 70 L120 95 L50 88 Z" fill="none"
        stroke="var(--dot)" stroke-width="1.5" stroke-dasharray="4 5" />
      <circle cx="30" cy="60" r="6" style="fill: var(--green)" />
      <circle cx="80" cy="30" r="5" style="fill: var(--blue)" />
      <circle cx="150" cy="40" r="7" style="fill: var(--bright)" />
      <circle cx="190" cy="70" r="5" style="fill: var(--green); opacity: .7" />
      <circle cx="120" cy="95" r="6" style="fill: var(--blue); opacity: .8" />
      <circle cx="50" cy="88" r="4" style="fill: var(--green); opacity: .5" />
      <circle cx="110" cy="62" r="14" fill="none" stroke="var(--dot)" stroke-width="2" stroke-dasharray="5 6" />
      <circle cx="110" cy="62" r="5" style="fill: var(--dot)" />
    </svg>
    <div class="e-title">{{ title }}</div>
    <div class="e-sub">{{ sub }}</div>
    <XButton v-if="actionText" @click="emit('action')">{{ actionText }}</XButton>
  </div>
</template>
