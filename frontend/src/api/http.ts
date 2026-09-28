// Shared JSON request helper. Authentication and SSE will be added separately.
export async function getJson<T>(path: string): Promise<T> {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), 8000)
  try {
    const response = await fetch(`/api${path}`, {
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    })
    if (!response.ok) throw new Error(`请求失败（HTTP ${response.status}），请检查后端服务。`)
    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('json')) throw new Error('接口未返回 JSON，请检查 API 代理配置。')
    return await response.json() as T
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw new Error('请求超时，请检查后端是否已启动。')
    if (error instanceof TypeError) throw new Error('无法连接服务，请检查网络和后端配置。')
    throw error
  } finally {
    window.clearTimeout(timer)
  }
}
