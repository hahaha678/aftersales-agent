import type { OrderStatus } from '../api/types'
export const statuses: Record<OrderStatus, string> = {
    PENDING_PAYMENT: '待付款',
    PAID: '待发货',
    SHIPPED: '运输中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
}
export function dateTime(value: string | null): string {
    if (!value) return '—'
    return new Date(value).toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    })
}
export function money(value: string) {
    return `¥ ${value}`
}
