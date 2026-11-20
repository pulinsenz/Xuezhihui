/**
 * 命令式轻提示：window 派发 x-toast 事件，App.vue 常驻的 <XToast /> 监听渲染。
 * toast.success('已保存') / toast.error('失败')
 */
function dispatch(text, kind) {
  window.dispatchEvent(new CustomEvent('x-toast', { detail: { text, kind } }))
}

export const toast = {
  success(text) { dispatch(text, 'ok') },
  error(text) { dispatch(text, 'err') },
}
