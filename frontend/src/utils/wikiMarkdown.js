import DOMPurify from 'dompurify'
import { marked } from 'marked'

export function renderWikiMarkdown(content, pages, currentId, pageHref) {
  const body = DOMPurify.sanitize(marked.parse(content), {
    RETURN_DOM: true,
    FORBID_ATTR: ['data-wiki-page'],
  })
  const doc = body.ownerDocument
  const pagesByOrder = new Map()
  const pagesByTitle = new Map()
  for (const page of pages) {
    const target = String(page.id) !== String(currentId) && page.status !== 'FAILED' ? page : null
    if (target) pagesByOrder.set(`#wiki-page-${page.sortOrder}`, target)
    const title = page.title?.trim()
    if (title) pagesByTitle.set(title, pagesByTitle.has(title) ? null : target)
  }

  const linked = new Set()
  const createLink = (page, ...children) => {
    const id = String(page.id)
    const link = doc.createElement('a')
    link.href = pageHref(id)
    link.dataset.wikiPage = id
    link.append(...children)
    linked.add(id)
    return link
  }

  // 章节序号只在当前 Wiki 中解析，目标页失败或不存在时保留链接文字。
  const unavailableLinks = []
  for (const link of body.querySelectorAll('a[href^="#wiki-page-"]:not(svg a, math a)')) {
    const page = pagesByOrder.get(link.getAttribute('href'))
    if (page) link.replaceWith(createLink(page, ...link.childNodes))
    else unavailableLinks.push(link)
  }

  if ([...pagesByTitle.values()].some((page) => page && !linked.has(String(page.id)))) {
    linkTitleMentions(body, pagesByTitle, linked, createLink)
  }
  for (const link of unavailableLinks) link.replaceWith(...link.childNodes)
  return body.innerHTML
}

function linkTitleMentions(body, pagesByTitle, linked, createLink) {
  const doc = body.ownerDocument
  const titles = [...pagesByTitle.keys()].sort((a, b) => b.length - a.length)
  const titlePattern = new RegExp(titles.map((title) => {
    const escaped = title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    return `${/^\w/.test(title) ? '(?<!\\w)' : ''}${escaped}${/\w$/.test(title) ? '(?!\\w)' : ''}`
  }).join('|'), 'g')

  // 只处理正文文本节点，保留代码、已有链接和来源列表。
  const walker = doc.createTreeWalker(body, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT)
  const textNodes = []
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (node.matches?.('h1, h2, h3, h4, h5, h6') && node.textContent.trim() === '来源') break
    if (node.nodeType === Node.TEXT_NODE && !node.parentElement.closest('a, code, pre, svg, math, textarea, select, button')) {
      textNodes.push(node)
    }
  }

  for (const node of textNodes) {
    const text = node.nodeValue
    const replacement = doc.createDocumentFragment()
    let offset = 0
    for (const match of text.matchAll(titlePattern)) {
      const [title] = match
      const page = pagesByTitle.get(title)
      if (!page || linked.has(String(page.id))) continue
      replacement.append(text.slice(offset, match.index), createLink(page, title))
      offset = match.index + title.length
    }
    if (offset) {
      replacement.append(text.slice(offset))
      node.replaceWith(replacement)
    }
  }
}
