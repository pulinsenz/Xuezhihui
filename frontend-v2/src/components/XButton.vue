<script setup>
defineProps({
  type: { type: String, default: 'primary' }, // primary | plain | danger-plain | soft
  size: { type: String, default: '' }, // '' | sm
  loading: Boolean,
  disabled: Boolean,
})
</script>

<template>
  <button class="x-btn" :class="[type, size]" :disabled="disabled || loading">
    <span v-if="loading" class="spinner" />
    <slot />
  </button>
</template>
