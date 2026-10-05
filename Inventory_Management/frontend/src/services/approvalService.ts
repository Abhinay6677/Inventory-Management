import { api } from '../api/client'
import type {
  ApprovalRequestResponse,
  ApprovalStatus,
  ProductRequest,
  StockUpdateRequest,
} from '../api/contracts'

export const approvalService = {
  requestProductCreate: async (payload: ProductRequest) => {
    const { data } = await api.post<ApprovalRequestResponse>('/approvals/products', payload)
    return data
  },
  requestProductUpdate: async (productId: number, payload: ProductRequest) => {
    const { data } = await api.put<ApprovalRequestResponse>(`/approvals/products/${productId}`, payload)
    return data
  },
  requestProductDelete: async (productId: number) => {
    const { data } = await api.delete<ApprovalRequestResponse>(`/approvals/products/${productId}`)
    return data
  },
  getProductRequests: async (status: ApprovalStatus = 'pending') => {
    const { data } = await api.get<ApprovalRequestResponse[]>('/approvals/products', { params: { status } })
    return data
  },
  approveProductRequest: async (requestId: number, reviewNotes?: string) => {
    const { data } = await api.patch<ApprovalRequestResponse>(`/approvals/products/${requestId}/approve`, {
      reviewNotes,
    })
    return data
  },
  rejectProductRequest: async (requestId: number, reviewNotes?: string) => {
    const { data } = await api.patch<ApprovalRequestResponse>(`/approvals/products/${requestId}/reject`, {
      reviewNotes,
    })
    return data
  },
  requestStockMovement: async (productId: number, payload: StockUpdateRequest) => {
    const { data } = await api.post<ApprovalRequestResponse>(`/approvals/stock/${productId}`, payload)
    return data
  },
  getStockRequests: async (status: ApprovalStatus = 'pending') => {
    const { data } = await api.get<ApprovalRequestResponse[]>('/approvals/stock', { params: { status } })
    return data
  },
  approveStockRequest: async (requestId: number, reviewNotes?: string) => {
    const { data } = await api.patch<ApprovalRequestResponse>(`/approvals/stock/${requestId}/approve`, {
      reviewNotes,
    })
    return data
  },
  rejectStockRequest: async (requestId: number, reviewNotes?: string) => {
    const { data } = await api.patch<ApprovalRequestResponse>(`/approvals/stock/${requestId}/reject`, {
      reviewNotes,
    })
    return data
  },
}
