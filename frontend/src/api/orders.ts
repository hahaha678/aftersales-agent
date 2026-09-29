import { request } from './http'
import type { OrderDetail, OrderPage, Shipment } from './types'
export const getOrders = (params: URLSearchParams, signal?: AbortSignal) =>
    request<OrderPage>(`/orders?${params}`, { signal })
export const getOrder = (id: string, signal?: AbortSignal) =>
    request<OrderDetail>(`/orders/${encodeURIComponent(id)}`, { signal })
export const getShipments = (id: string, signal?: AbortSignal) =>
    request<Shipment[]>(`/orders/${encodeURIComponent(id)}/shipments`, { signal })
