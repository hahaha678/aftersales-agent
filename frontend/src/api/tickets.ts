import { request } from './http'
import type { Message } from './agent'
export const ticketStatuses: Record<string, string> = { OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决' }
export interface Ticket {
    id: string
    conversationId: string
    problem: string
    orderId: string | null
    aftersaleId: string | null
    status: string
    assigneeId: string | null
    resolution: string | null
    createdAt: string
    updatedAt: string
    events: { action: string; note: string; occurredAt: string }[]
}
export interface TicketInput {
    conversationId: string
    requestKey: string
    problem: string
    orderId: string | null
    aftersaleId: string | null
}
export const createTicket = (body: TicketInput) => request<Ticket>('/tickets', { method: 'POST', body })
const root = (staff: boolean) => (staff ? '/staff/tickets' : '/tickets')
export const listTickets = (staff: boolean, status: string, before?: string, signal?: AbortSignal) => {
    const query = new URLSearchParams()
    if (status) query.set('status', status)
    if (before) query.set('before', before)
    return request<Ticket[]>(`${root(staff)}?${query}`, { signal })
}
export const getTicket = (id: string, staff: boolean, signal?: AbortSignal) =>
    request<Ticket>(`${root(staff)}/${encodeURIComponent(id)}`, { signal })
export const ticketContext = (id: string, staff: boolean, before?: string, signal?: AbortSignal) =>
    request<Message[]>(
        `${root(staff)}/${encodeURIComponent(id)}/context${before ? '?before=' + encodeURIComponent(before) : ''}`,
        { signal },
    )
export const claimTicket = (id: string) =>
    request<Ticket>(`/staff/tickets/${encodeURIComponent(id)}/assignment`, { method: 'PUT' })
export const resolveTicket = (id: string, resolution: string) =>
    request<Ticket>(`/staff/tickets/${encodeURIComponent(id)}/resolution`, { method: 'PUT', body: { resolution } })
