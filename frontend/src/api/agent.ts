import { request, ApiError } from './http'
import { session, clearSession } from '../state/session'
import type { Aftersale } from './aftersales'
export interface Conversation {
    id: string
    title: string
    createdAt: string
}
export interface Message {
    id: string
    runId: string
    role: string
    content: string
    status: string
    createdAt: string
}
export interface Run {
    id: string
    conversationId: string
    status: string
    content: string
    errorMessage: string | null
    model: string
    inputTokens: number
    outputTokens: number
    createdAt: string
}
export interface Draft {
    id: string
    conversationId: string
    orderId: string
    orderItemId: string
    orderNumber: string
    productName: string
    quantity: number
    reason: string
    description: string
    amount: string
    version: number
    status: string
    aftersaleId: string | null
    ruleVersion: string
    expiresAt: string
}
export interface Confirmation {
    confirmed: boolean
    draft: Draft
    application: Aftersale | null
    message: string
}
export const agentStatus = (signal?: AbortSignal) =>
    request<{ available: boolean; model: string; message: string }>('/agent/status', { signal })
export const conversations = (signal?: AbortSignal) => request<Conversation[]>('/conversations', { signal })
export const createConversation = (title: string) =>
    request<Conversation>('/conversations', { method: 'POST', body: { title } })
export const messages = (id: string, before?: string, signal?: AbortSignal) =>
    request<Message[]>(`/conversations/${id}/messages${before ? '?before=' + before : ''}`, { signal })
export const activeRun = (id: string, signal?: AbortSignal) =>
    request<Run | undefined>(`/conversations/${id}/active-run`, { signal })
export const sendMessage = (id: string, content: string, requestKey: string) =>
    request<Run>(`/conversations/${id}/messages`, { method: 'POST', body: { content, requestKey } })
export const stopRun = (id: string) => request<Run>(`/agent-runs/${id}/cancellation`, { method: 'POST' })
export const drafts = (id: string, signal?: AbortSignal) => request<Draft[]>(`/conversations/${id}/drafts`, { signal })
export const confirmDraft = (draft: Draft) =>
    request<Confirmation>(`/aftersale-drafts/${draft.id}/confirmation`, {
        method: 'POST',
        body: { version: draft.version, confirmed: true },
    })
export const cancelDraft = (id: string) => request<Draft>(`/aftersale-drafts/${id}/cancellation`, { method: 'POST' })
export type StreamEvent = { name: string; data: unknown }
// 使用 fetch 读取 SSE，便于携带 Bearer 请求头，避免把令牌放入 URL。
export async function streamRun(id: string, signal: AbortSignal, onEvent: (event: StreamEvent) => void): Promise<void> {
    const token = session.token
    const response = await fetch(`/api/agent-runs/${id}/events`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        credentials: 'omit',
        cache: 'no-store',
        signal,
    })
    if (!response.ok) {
        const body = (await response.json().catch(() => ({}))) as { message?: string; requestId?: string }
        if (response.status === 401 && token === session.token) {
            clearSession()
            window.dispatchEvent(new Event('session-expired'))
        }
        throw new ApiError(body.message || '无法读取回答，请刷新会话。', response.status, body.requestId)
    }
    if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream'))
        throw new Error('未收到有效的回答流。')
    const reader = response.body.getReader(),
        decoder = new TextDecoder()
    let buffer = '',
        finished = false
    // 网络分片不一定是完整事件，先缓冲，再按空行拆分 SSE 帧。
    const parse = () => {
        let newline: number
        while ((newline = buffer.indexOf('\n\n')) >= 0) {
            const frame = buffer.slice(0, newline)
            buffer = buffer.slice(newline + 2)
            const lines = frame.split('\n'),
                name =
                    lines
                        .find((line) => line.startsWith('event:'))
                        ?.slice(6)
                        .trim() || 'message'
            const data = lines
                .filter((line) => line.startsWith('data:'))
                .map((line) => line.slice(5).trimStart())
                .join('\n')
            if (data) {
                onEvent({ name, data: JSON.parse(data) })
                if (name === 'done') finished = true
            }
        }
    }
    // Normalize CRLF after concatenation so a split CR/LF boundary remains valid.
    try {
        while (!finished) {
            const { value, done } = await reader.read()
            buffer += decoder.decode(value, { stream: !done })
            buffer = buffer.replace(/\r\n/g, '\n')
            parse()
            if (done) break
        }
        if (!finished && !signal.aborted)
            throw new Error('连接已断开，任务可能仍在执行。请点击刷新会话恢复，勿重复发送。')
    } finally {
        await reader.cancel().catch(() => {})
        reader.releaseLock()
    }
}
