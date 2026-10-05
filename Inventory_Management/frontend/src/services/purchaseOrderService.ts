import { api } from '../api/client'
import type { POStatus, PurchaseOrderRequest, PurchaseOrderResponse } from '../api/contracts'

export const purchaseOrderService = {
  getOrders: async (params?: { status?: POStatus; supplierId?: number }) => {
    const { data } = await api.get<PurchaseOrderResponse[]>('/orders', { params })
    return data
  },
  getOrderById: async (id: number) => {
    const { data } = await api.get<PurchaseOrderResponse>(`/orders/${id}`)
    return data
  },
  createOrder: async (payload: PurchaseOrderRequest) => {
    const { data } = await api.post<PurchaseOrderResponse>('/orders', payload)
    return data
  },
  submitOrder: async (id: number) => {
    const { data } = await api.patch<PurchaseOrderResponse>(`/orders/${id}/submit`)
    return data
  },
  cancelOrder: async (id: number) => {
    const { data } = await api.patch<PurchaseOrderResponse>(`/orders/${id}/cancel`)
    return data
  },
  approveOrder: async (id: number) => {
    const { data } = await api.patch<PurchaseOrderResponse>(`/orders/${id}/approve`)
    return data
  },
  receiveOrder: async (id: number) => {
    const { data } = await api.patch<PurchaseOrderResponse>(`/orders/${id}/receive`)
    return data
  },
}
