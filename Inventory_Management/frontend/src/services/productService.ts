import { api } from '../api/client'
import type { Category, ProductReorderRequest, ProductRequest, ProductResponse, StockUpdateRequest } from '../api/contracts'

export const productService = {
  getProducts: async (params?: { category?: Category; lowStock?: boolean }) => {
    const { data } = await api.get<ProductResponse[]>('/products', { params })
    return data
  },
  getProductById: async (id: number) => {
    const { data } = await api.get<ProductResponse>(`/products/${id}`)
    return data
  },
  createProduct: async (payload: ProductRequest) => {
    const { data } = await api.post<ProductResponse>('/products', payload)
    return data
  },
  updateProduct: async (id: number, payload: ProductRequest) => {
    const { data } = await api.put<ProductResponse>(`/products/${id}`, payload)
    return data
  },
  updateReorderSettings: async (id: number, payload: ProductReorderRequest) => {
    const { data } = await api.patch<ProductResponse>(`/products/${id}/reorder-settings`, payload)
    return data
  },
  deleteProduct: async (id: number) => {
    await api.delete(`/products/${id}`)
  },
  updateStock: async (id: number, payload: StockUpdateRequest) => {
    const { data } = await api.patch<ProductResponse>(`/products/${id}/stock`, payload)
    return data
  },
}
