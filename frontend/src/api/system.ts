import { getJson } from './http'

export interface HealthResponse {
  status: string
}

export async function getBackendHealth(): Promise<HealthResponse> {
  const result = await getJson<HealthResponse>('/system/health')
  if (!result || typeof result.status !== 'string') throw new Error('健康接口数据格式不正确。')
  return result
}
