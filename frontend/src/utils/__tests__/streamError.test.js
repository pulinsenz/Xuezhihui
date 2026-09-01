import { describe, expect, it } from 'vitest'
import { parseStreamError } from '../streamError'

describe('parseStreamError', () => {
  it('解析业务错误体并取出 message/code', () => {
    const r = parseStreamError('{"code":40100,"message":"登录已失效，请重新登录","data":null}')
    expect(r.message).toBe('登录已失效，请重新登录')
    expect(r.code).toBe(40100)
  })

  it('无权使用会话等非登录错误同样透出 message', () => {
    const r = parseStreamError('{"code":40101,"message":"无权使用该会话","data":null}')
    expect(r.message).toBe('无权使用该会话')
    expect(r.code).toBe(40101)
  })

  it('message 缺失时给通用兜底', () => {
    const r = parseStreamError('{"code":50000}')
    expect(r.message).toBe('对话请求失败，请稍后再试')
  })

  it('非 JSON 输入兜底为通用错误', () => {
    const r = parseStreamError('<html>502 Bad Gateway</html>')
    expect(r.code).toBeNull()
    expect(r.message).toBe('对话请求失败，请稍后再试')
  })
})
