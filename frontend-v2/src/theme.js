/**
 * 主题系统：青薄荷（默认）/ 薇紫 / 墨夜。
 * 主题 = 一组 CSS 变量，通过 html[data-theme] 生效；布局与组件零改动。
 * 偏好存 localStorage（未登录也生效、刷新不闪变，index.html 有同逻辑的防闪变脚本）。
 * 后续如需账号级同步：登录后在 auth store 里调 PUT /settings/theme（后端加字段即可）。
 */
const KEY = 'xz-theme'

export const THEMES = [
  {
    key: 'mint',
    name: '青薄荷',
    desc: '默认 · 浅绿+鲜绿+蓝',
    swatches: ['#3E9B4F', '#35D461', '#2F6FED'],
  },
  {
    key: 'grape',
    name: '薇紫',
    desc: '浅紫+亮紫+青',
    swatches: ['#6D4FD8', '#9B6BFF', '#0E9CBF'],
  },
  {
    key: 'night',
    name: '墨夜',
    desc: '暗色模式 · 深绿底',
    swatches: ['#2E8F55', '#7CE495', '#5B9BF5'],
  },
]

export function getTheme() {
  const t = localStorage.getItem(KEY)
  return THEMES.some((x) => x.key === t) ? t : 'mint'
}

export function setTheme(key) {
  if (!THEMES.some((x) => x.key === key)) return
  localStorage.setItem(KEY, key)
  document.documentElement.dataset.theme = key
}

export function initTheme() {
  document.documentElement.dataset.theme = getTheme()
}
