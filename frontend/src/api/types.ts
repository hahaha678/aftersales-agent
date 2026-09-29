export interface User {
    id: string
    username: string
    displayName: string
    role: 'CUSTOMER' | 'STAFF'
}
export interface LoginResult {
    accessToken: string
    tokenType: string
    expiresAt: string
    user: User
}
export type OrderStatus = 'PENDING_PAYMENT' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELLED'
export interface OrderSummary {
    id: string
    orderNumber: string
    status: OrderStatus
    totalQuantity: number
    paidAmount: string
    currency: string
    createdAt: string
}
export interface OrderPage {
    items: OrderSummary[]
    page: number
    size: number
    total: number
}
export interface OrderItem {
    id: string
    skuId: string
    productName: string
    specification: string
    quantity: number
    paidAmount: string
    availableAftersalesQuantity: number
}
export interface OrderDetail {
    id: string
    orderNumber: string
    status: OrderStatus
    paidAmount: string
    currency: string
    createdAt: string
    paidAt: string | null
    signedAt: string | null
    items: OrderItem[]
}
export interface Shipment {
    id: string
    carrier: string
    trackingNumber: string
    status: 'IN_TRANSIT' | 'DELIVERED' | 'EXCEPTION'
    events: { occurredAt: string; description: string }[]
}
