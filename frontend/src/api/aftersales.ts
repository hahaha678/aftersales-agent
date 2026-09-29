import { request } from './http'
export const aftersaleStatuses: Record<string, string> = {
    PENDING: '待审核',
    APPROVED: '审核通过 · 待退货',
    RETURN_SHIPPED: '已登记退回物流 · 待收货',
    RETURN_RECEIVED: '客服已收货 · 尚未退款',
    REFUND_PENDING: '模拟退款结果待确认',
    REFUND_FAILED: '模拟退款失败',
    COMPLETED: '售后完成 · 模拟退款成功',
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
    returnShipment: { carrier: string; trackingNumber: string; registeredAt: string } | null
    receipt: { note: string; receivedAt: string } | null
    refunds: Refund[]
}
export interface Refund {
    requestKey: string
    operationNumber: string
    amount: string
    status: 'SUCCEEDED' | 'FAILED' | 'UNKNOWN'
    createdAt: string
    updatedAt: string
}
export type RefundMode = 'SUCCESS' | 'FAILURE' | 'TIMEOUT_SUCCESS' | 'TIMEOUT_FAILURE'
export const refundStatuses = { SUCCEEDED: '模拟成功', FAILED: '明确失败', UNKNOWN: '结果未知，需查询' }
export const submitSimulatedRefund = (id: string, key: string, mode: RefundMode, previousKey: string | null) =>
    request<Aftersale>(`/staff/aftersales/${encodeURIComponent(id)}/refunds/${encodeURIComponent(key)}`, {
        method: 'PUT',
        body: { mode, previousKey },
    })
export const reconcileRefund = (id: string, key: string) =>
    request<Aftersale>(
        `/staff/aftersales/${encodeURIComponent(id)}/refunds/${encodeURIComponent(key)}/reconciliation`,
        {
            method: 'POST',
        },
    )
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
export const registerReturnShipment = (id: string, carrier: string, trackingNumber: string) =>
    request<Aftersale>(`/aftersales/${encodeURIComponent(id)}/return-shipment`, {
        method: 'PUT',
        body: { carrier, trackingNumber },
    })
export const confirmReturnReceipt = (id: string, note: string) =>
    request<Aftersale>(`/staff/aftersales/${encodeURIComponent(id)}/receipt`, { method: 'PUT', body: { note } })
export const reviewAftersale = (id: string, decision: string, note: string) =>
    request<Aftersale>(`/staff/aftersales/${encodeURIComponent(id)}/review`, {
        method: 'POST',
        body: { decision, note },
    })
