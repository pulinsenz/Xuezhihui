import { Marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * 对话流专用 Markdown 渲染：LLM 输出 → 安全 HTML。
 * 与 WikiView 的默认配置不同：聊天需要 breaks（单换行即换行），
 * 故独立实例，不复用全局 marked 配置。
 */
const md = new Marked({ breaks: true, gfm: true })

// 叶子文本节点里高亮引用编号 [n]；带内嵌 tokens（加粗/斜体等）返回 false 走默认递归，
// 内层叶子仍会经过本 renderer，引用高亮不丢；代码块/行内代码走独立 renderer，不受影响
md.use({
  renderer: {
    text(token) {
      if (token.tokens?.length) return false
      return String(token.text ?? '').replace(
        /\[(\d+)\]/g,
        '<span class="cite">[$1]</span>'
      )
    },
  },
})

// 外部链接新窗口打开，避免点开资料链接时冲走应用（对全站 sanitize 生效）
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.getAttribute('href')?.startsWith('http')) {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

export function renderMarkdown(text) {
  return DOMPurify.sanitize(md.parse(text || '', { async: false }))
}
