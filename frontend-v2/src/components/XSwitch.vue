<script setup>
const props = defineProps({
  modelValue: { type: [Number, Boolean], default: 0 },
  disabled: Boolean,
})
const emit = defineEmits(['update:modelValue', 'change'])

function toggle() {
  if (props.disabled) return
  const next = props.modelValue ? 0 : 1
  emit('update:modelValue', next)
  emit('change', next)
}
</script>

<template>
  <!-- 开关：拨动立即生效（调用方自行调接口，失败则回拨） -->
  <button
    class="x-switch" :class="{ on: !!modelValue }" :disabled="disabled"
    role="switch" :aria-checked="!!modelValue" @click="toggle"
  />
</template>
