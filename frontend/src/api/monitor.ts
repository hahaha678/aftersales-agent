import { request } from './http'
export const runStatuses: Record<string, string> = {
    RUNNING: '执行中',
    SUCCEEDED: '执行成功',
    FAILED: '执行失败',
    CANCELLED: '已取消',
    EXPIRED: '租约已过期',
}
export interface MonitorRun {
    id: string
    conversationId: string
    status: string
    storedStatus: string
    model: string
    inputTokens: number
    outputTokens: number
    durationMs: number
    createdAt: string
    finishedAt: string | null
    toolCalls: number
    failedToolCalls: number
    errorCode: string | null
    ownConversation: boolean
    userContent: string | null
    assistantContent: string | null
    ticketIds: string[]
}
export interface ToolCall {
    id: string
    toolName: string
    status: string
    durationMs: number
    callIndex: number | null
    startedAt: string | null
    finishedAt: string
    inputSummary: string | null
    resultSummary: string | null
    errorCode: string | null
}
export interface MonitorPage {
    items: MonitorRun[]
    page: number
    size: number
    total: number
}
export const monitorRuns = (query: URLSearchParams, signal?: AbortSignal) =>
    request<MonitorPage>(`/staff/agent-runs?${query}`, { signal })
export const monitorRun = (id: string, signal?: AbortSignal) =>
    request<MonitorRun>(`/staff/agent-runs/${encodeURIComponent(id)}`, { signal })
export const monitorTools = (id: string, signal?: AbortSignal) =>
    request<ToolCall[]>(`/staff/agent-runs/${encodeURIComponent(id)}/tool-calls`, { signal })
