import { onActivated, onBeforeUnmount } from 'vue'

// 只记录改动版本，页面数据仍由各页签实例持有，不落浏览器存储。
const knowledgeTabVersion = { list: 0, docs: 0, wiki: 0 }

export function markKnowledgeChanged(...areas) {
  for (const area of areas) knowledgeTabVersion[area]++
}

export async function trackKnowledgeChange(request, ...areas) {
  const result = await request
  markKnowledgeChanged(...areas)
  return result
}

export function useTabRefresh(load, areas, maxAge = 60_000) {
  let loadedAt
  let versions
  let pendingVersions
  let requestId = 0
  let disposed = false

  async function refresh(quiet = false) {
    if (disposed) return
    const id = ++requestId
    const isCurrent = () => !disposed && id === requestId
    const startVersions = areas.map((area) => knowledgeTabVersion[area])
    pendingVersions = startVersions
    try {
      const result = await load(quiet, isCurrent)
      if (isCurrent() && result !== false) {
        loadedAt = Date.now()
        versions = startVersions
      }
    } catch {
      // 请求层已提示失败，下次进入时仍可重试。
    } finally {
      if (isCurrent()) pendingVersions = undefined
    }
  }

  onActivated(() => {
    // KeepAlive 首次挂载也会触发；初次加载由页面自身负责。
    if (!requestId) return
    const lastVersions = pendingVersions ?? versions
    const changed = areas.some((area, index) => knowledgeTabVersion[area] !== lastVersions?.[index])
    const expired = !pendingVersions && (loadedAt === undefined || Date.now() - loadedAt >= maxAge)
    if (changed || expired) refresh(loadedAt !== undefined)
  })

  onBeforeUnmount(() => { disposed = true })

  return refresh
}
