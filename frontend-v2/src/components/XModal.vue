<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
import XIcon from './XIcon.vue'

/**
 * 非填表 Modal：点遮罩 / Esc 可关闭（表单类弹窗调用方传 lockMask 禁用蒙层关闭）。
 * 出场动画见 ui.css 的弹簧缓动。
 */
const props = defineProps({
  title: { type: String, default: '' },
  wide: Boolean,
  lockMask: Boolean, // 填表弹窗禁点蒙层关闭（手一滑数据全丢）
})
const emit = defineEmits(['close'])

function onKeydown(e) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <div class="modal-mask" @click="!props.lockMask && emit('close')">
      <div class="modal modal-enter" :class="{ wide: props.wide }" @click.stop>
        <div class="modal-head">
          <b>{{ props.title }}</b>
          <slot name="head-extra" />
          <button class="icon-btn" style="margin-left: auto" title="关闭 (Esc)" @click="emit('close')">
            <XIcon name="close" />
          </button>
        </div>
        <div class="modal-body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="modal-foot">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>
