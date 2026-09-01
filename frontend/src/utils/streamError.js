/**
 * 解析 chat SSE 流的错误响应体。
 * <p>
 * Java 业务异常（未登录/登录失效/无权使用会话/限流等）以 HTTP 200 + application/json
 * 返回，而非 SSE 事件。前端若不显式解析，SSE 解析器找不到 data: 行会静默落成
 * 「（无回答）」，把真实错误吞掉。此函数提取 message 与业务 code 供上层展示/处理。
 *
 * @returns {{ message: string, code: number|null }}
 */
export function parseStreamError(text) {
  try {
    const body = JSON.parse(text)
    return {
      message: (body && body.message) || '对话请求失败，请稍后再试',
      code: body ? body.code : null,
    }
  } catch (e) {
    return { message: '对话请求失败，请稍后再试', code: null }
  }
}
