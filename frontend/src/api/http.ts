import { session, clearSession } from '../state/session'
export class ApiError extends Error {
  constructor(message: string, public status: number, public requestId = '') { super(message) }
}
export async function request<T>(path: string, options: { method?: string; body?: unknown; auth?: boolean; signal?: AbortSignal } = {}): Promise<T> {
  const controller = new AbortController()
  const abort = () => controller.abort()
  options.signal?.addEventListener('abort', abort, { once: true })
  if (options.signal?.aborted) controller.abort()
  const timer = window.setTimeout(abort, 10000)
  const token = options.auth === false ? '' : session.token
  try {
    const response = await fetch(`/api${path}`, {
      method: options.method || 'GET', credentials: 'omit', cache: 'no-store',
      headers: { Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}) },
      body: options.body === undefined ? undefined : JSON.stringify(options.body), signal: controller.signal,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({})) as { message?: string; requestId?: string }
      if (response.status === 401 && token && token === session.token) {
        clearSession(); window.dispatchEvent(new Event('session-expired'))
      }
      const fallback = response.status === 503 ? '服务暂不可用，请稍后重试。'
        : response.status === 501 ? '此功能尚未启用，请确认后端使用 local 配置运行。'
          : `请求失败（HTTP ${response.status}），请稍后重试。`
      throw new ApiError(response.status === 501 ? fallback : body.message || fallback,
        response.status, body.requestId || response.headers.get('X-Request-Id') || '')
    }
    if (response.status === 204) return undefined as T
    if (!response.headers.get('content-type')?.includes('json')) throw new Error('服务返回了意外内容，请检查连接。')
    return await response.json() as T
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      if (options.signal?.aborted) throw error
      throw new Error('请求超时，请稍后重试。')
    }
    if (error instanceof TypeError) throw new Error('无法连接服务，请检查网络和后端是否启动。')
    throw error
  } finally { window.clearTimeout(timer); options.signal?.removeEventListener('abort', abort) }
}
export function getJson<T>(path: string): Promise<T> { return request<T>(path, { auth: false }) }
