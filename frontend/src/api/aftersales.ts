import { request } from './http'
export const aftersaleStatuses: Record<string, string> = {
    PENDING: '待审核',
    APPROVED: '审核通过 · 待退货',
    REJECTED: '已拒绝',
    CANCELLED: '已撤销',
}
export const reasons: Record<string, string> = {
    QUALITY: '质量问题',
    DAMAGED: '商品破损',
    WRONG_ITEM: '发错商品',
    NO_LONGER_NEEDED: '不再需要',
    OTHER: '其他原因',
}
export interface Eligibility {
    orderId: string
    ruleVersion: string
    deadline: string | null
    items: {
        orderItemId: string
        productName: string
        availableQuantity: number
        remainingAmount: string
        eligible: boolean
        reason: string | null
    }[]
}
export interface Aftersale {
    id: string
    orderId: string
    orderItemId: string
    orderNumber: string
    productName: string
    specification: string
    quantity: number
    amount: string
    currency: string
    type: string
    reason: string
    description: string
    status: string
    ruleVersion: string
    createdAt: string
    updatedAt: string
    events: { action: string; note: string; occurredAt: string }[]
}
export interface AftersalePage {
    items: Aftersale[]
    page: number
    size: number
    total: number
}
export interface CreateAftersale {
    orderId: string
    orderItemId: string
    quantity: number
    reason: string
    description: string
    requestKey: string
}
export const getEligibility = (id: string, signal?: AbortSignal) =>
    request<Eligibility>(`/orders/${encodeURIComponent(id)}/aftersale-eligibility`, { signal })
export const createAftersale = (body: CreateAftersale) => request<Aftersale>('/aftersales', { method: 'POST', body })
export const listAftersales = (query: URLSearchParams, staff: boolean, signal?: AbortSignal) =>
    request<AftersalePage>(`${staff ? '/staff' : ''}/aftersales?${query}`, { signal })
export const getAftersale = (id: string, staff: boolean, signal?: AbortSignal) =>
    request<Aftersale>(`${staff ? '/staff' : ''}/aftersales/${encodeURIComponent(id)}`, { signal })
export const cancelAftersale = (id: string) =>
    request<Aftersale>(`/aftersales/${encodeURIComponent(id)}/cancellation`, { method: 'POST' })
export const reviewAftersale = (id: string, decision: string, note: string) =>
    request<Aftersale>(`/staff/aftersales/${encodeURIComponent(id)}/review`, {
        method: 'POST',
        body: { decision, note },
    })
