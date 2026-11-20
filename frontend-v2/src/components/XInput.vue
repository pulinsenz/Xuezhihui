<script setup>
import { computed } from 'vue'
import XIcon from './XIcon.vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  placeholder: { type: String, default: '' },
  type: { type: String, default: 'text' }, // text | password
  icon: { type: String, default: '' },
  invalid: Boolean,
})
const emit = defineEmits(['update:modelValue', 'enter'])

const showPwd = defineModel('showPwd', { type: Boolean, default: false })
const realType = computed(() => (props.type === 'password' && !showPwd.value ? 'password' : props.type))
</script>

<template>
  <div class="x-input" :class="{ invalid }">
    <span v-if="icon" class="prefix"><XIcon :name="icon" /></span>
    <input
      :type="realType"
      :value="modelValue"
      :placeholder="placeholder"
      @input="emit('update:modelValue', $event.target.value)"
      @keyup.enter="emit('enter', $event)"
    />
    <span v-if="type === 'password'" class="suffix" @click="showPwd = !showPwd">
      {{ showPwd ? '隐藏' : '显示' }}
    </span>
    <slot name="suffix" />
  </div>
</template>
